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
import cats.syntax.all._
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.{JSONObject, JSONTokener}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.GoodsItemRecordsController.ValidatedHeaders
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateGoodsItemRecordRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class GoodsItemRecordsController @Inject()(
                                            override val controllerComponents: ControllerComponents,
                                            goodsItemRecordRepository: GoodsItemRecordRepository,
                                            uuidService: UuidService,
                                            clock: Clock,
                                            configuration: Configuration,
                                            environment: Environment,
                                            schemaValidationService: SchemaValidationService
                                          )(implicit ec: ExecutionContext) extends BackendBaseController {

  private val expectedAuthHeader: String = configuration.get[String]("expected-auth-header")
  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")

  // Using `get` here as we want to throw an exception on startup if this can't be found
  private val createRecordSchema: Schema = schemaValidationService.createSchema("/schemas/tgp-create-record-request-v0.7.json").get

  def createRecord(): Action[AnyContent] = Action.async { implicit request =>

    val result = for {
      _                <- validateAuthorization(request)
      validatedHeaders <- validateHeaders(request)
      body             <- validateCreateGoodsRecordItemRequest(request)
    } yield {

      goodsItemRecordRepository.insert(body).map { goodsItemRecord =>

        Created(goodsItemRecord.toCreateRecordResponse)
          .withHeaders(
            "X-Correlation-ID" -> validatedHeaders.correlationId,
            "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
            "Content-Type" -> "application/json"
          )
      }
    }

    result.leftMap(Future.successful).merge
  }

  def getRecords(eori: String): Action[AnyContent] =
    Action(NotImplemented)

  def getRecord(eori: String, recordId: String): Action[AnyContent] =
    Action(NotImplemented)

  def updateRecord(): Action[AnyContent] =
    Action(NotImplemented)

  def removeRecord(): Action[AnyContent] =
    Action(NotImplemented)

  private def validateAuthorization(request: Request[_]): Either[Result, _] =
    request.headers.get("Authorization")
      .filter(_ == expectedAuthHeader)
      .toRight(Forbidden)

  private def validateCorrelationId(request: Request[_]): EitherNec[String, String] =
    request.headers.get("X-Correlation-Id").toRightNec("error: 001, message: Invalid Header")

  private def validateForwardedHost(request: Request[_]): EitherNec[String, String] =
    request.headers.get("X-Forwarded-Host").toRightNec("error: 005, message: Invalid Header")

  private def validateDate(request: Request[_]): EitherNec[String, _] =
    request.headers.get("Date").flatMap { dateString =>
      Try(rfc7231Formatter.parse(dateString)).toOption
    }.toRightNec("error: 002, message: Invalid Header")

  private def validateContentType(request: Request[_]): EitherNec[String, _] =
    request.headers.get("Content-Type")
      .filter(_ == "application/json")
      .toRightNec("error: 003, message: Invalid Header")

  private def validateHeaders(request: Request[_]): Either[Result, ValidatedHeaders] = {
    (
      validateCorrelationId(request),
      validateForwardedHost(request),
      validateDate(request),
      validateContentType(request)
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

  private def validateCreateGoodsRecordItemRequest(request: Request[AnyContent]): Either[Result, CreateGoodsItemRecordRequest] = {

    val json = request.body.asJson.get
    val validationErrors = schemaValidationService.validate(createRecordSchema, json)

    if (validationErrors.isEmpty) {
      Right(json.as[CreateGoodsItemRecordRequest])
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

object GoodsItemRecordsController {

  final case class ValidatedHeaders(correlationId: String, forwardedHost: String)
}
