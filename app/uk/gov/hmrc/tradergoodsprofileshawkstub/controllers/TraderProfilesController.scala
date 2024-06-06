/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.tradergoodsprofileshawkstub.controllers

import cats.data.EitherNec
import cats.implicits.catsStdInstancesForFuture
import cats.syntax.all._
import org.everit.json.schema.Schema
import play.api.Configuration
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.TraderProfilesController.ValidatedHeaders
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.MaintainTraderProfileRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class TraderProfilesController @Inject()(
                                          override val controllerComponents: ControllerComponents,
                                          clock: Clock,
                                          uuidService: UuidService,
                                          configuration: Configuration,
                                          schemaValidationService: SchemaValidationService,
                                          traderProfileRepository: TraderProfileRepository
                                        )(implicit ec: ExecutionContext) extends BackendBaseController {

  private val expectedAuthHeader: String = configuration.get[String]("expected-auth-header")

  // Using `get` here as we want to throw an exception on startup if this can't be found
  private val maintainProfileSchema: Schema = schemaValidationService.createSchema("/schemas/tgp-maintain-profile-request-v0.1.json").get

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")

  def maintainProfile(): Action[RawBuffer] = Action.async(parse.raw) { implicit request =>

    val result = for {
      _                <- validateAuthorization
      validatedHeaders <- validatePostHeaders
      body             <- validateRequestBody[MaintainTraderProfileRequest](maintainProfileSchema)
    } yield {
      traderProfileRepository.upsert(body).as {
        Ok(Json.toJson(body))
          .withHeaders(
            "X-Correlation-ID" -> validatedHeaders.correlationId,
            "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
            "Content-Type" -> "application/json"
          )
      }
    }

    result.leftMap(Future.successful).merge
  }

  private def validateAuthorization(implicit request: Request[_]): Either[Result, _] =
    request.headers.get("Authorization")
      .filter(_ == expectedAuthHeader)
      .toRight(Forbidden)

  private def validateCorrelationId(implicit request: Request[_]): EitherNec[String, String] =
    request.headers.get("X-Correlation-Id").toRightNec("error: 001, message: Invalid Header")

  private def validateForwardedHost(implicit request: Request[_]): EitherNec[String, String] =
    request.headers.get("X-Forwarded-Host").toRightNec("error: 005, message: Invalid Header")

  private def validateDate(implicit request: Request[_]): EitherNec[String, _] =
    request.headers.get("Date").flatMap { dateString =>
      Try(rfc7231Formatter.parse(dateString)).toOption
    }.toRightNec("error: 002, message: Invalid Header")

  private def validateContentType(implicit request: Request[_]): EitherNec[String, _] =
    request.headers.get("Content-Type")
      .filter(_ == "application/json")
      .toRightNec("error: 003, message: Invalid Header")

  private def validatePostHeaders(implicit request: Request[_]): Either[Result, ValidatedHeaders] = {
    (
      validateCorrelationId,
      validateForwardedHost,
      validateDate,
      validateContentType
    ).parMapN { (correlationId, forwardedHost, _, _) =>
      ValidatedHeaders(correlationId, forwardedHost)
    }.leftMap { errors =>

      val correlationId = request.headers.get("X-Correlation-Id").getOrElse(uuidService.generate())
      val forwardedHost = request.headers.get("X-Forwarded-Host")

      val headers = Seq(
        Some("X-Correlation-Id" -> correlationId),
        forwardedHost.map("X-Forwarded-Host" -> _),
        Some("Content-Type" -> "application/json")
      ).flatten

      BadRequest(Json.toJson(ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = errors.toList
      ))).withHeaders(headers: _*)
    }
  }

  private def validateJsonBody(implicit request: Request[RawBuffer]): Either[Result, JsValue] = {
    request.body.asBytes().toRight(EntityTooLarge).flatMap { byteString =>
      Try(Json.parse(byteString.utf8String)).toOption.toRight {

        val correlationId = request.headers.get("X-Correlation-Id").getOrElse(uuidService.generate())
        val forwardedHost = request.headers.get("X-Forwarded-Host")

        val headers = Seq(
          Some("X-Correlation-Id" -> correlationId),
          forwardedHost.map("X-Forwarded-Host" -> _),
          Some("Content-Type" -> "application/json")
        ).flatten

        BadRequest(Json.toJson(ErrorResponse(
          correlationId = correlationId,
          timestamp = clock.instant(),
          errorCode = "400",
          errorMessage = "Invalid message : Bad Request",
          source = "Json Validation",
          detail = Seq.empty
        ))).withHeaders(headers: _*)
      }
    }
  }

  private def validateRequestBody[A: Reads](schema: Schema)(implicit request: Request[RawBuffer]): Either[Result, A] = {
    validateJsonBody.flatMap { json =>

      val validationErrors = schemaValidationService.validate(schema, json)

      if (validationErrors.isEmpty) {
        Right(json.as[A])
      } else Left {

        val correlationId = request.headers.get("X-Correlation-Id").getOrElse(uuidService.generate())
        val forwardedHost = request.headers.get("X-Forwarded-Host")

        val headers = Seq(
          Some("X-Correlation-Id" -> correlationId),
          forwardedHost.map("X-Forwarded-Host" -> _),
          Some("Content-Type" -> "application/json")
        ).flatten

        BadRequest(Json.toJson(ErrorResponse(
          correlationId = correlationId,
          timestamp = clock.instant(),
          errorCode = "400",
          errorMessage = "Invalid message : Bad Request",
          source = "Json Validation",
          detail = validationErrors.map { error =>
            s"${error.key}: ${error.message}"
          }
        ))).withHeaders(headers: _*)
      }
    }
  }
}

object TraderProfilesController {

  final case class ValidatedHeaders(correlationId: String, forwardedHost: String)
}
