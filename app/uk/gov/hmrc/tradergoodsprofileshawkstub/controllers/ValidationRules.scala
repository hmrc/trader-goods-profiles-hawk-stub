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

import cats.data._
import cats.syntax.all._
import org.apache.pekko.Done
import org.everit.json.schema.Schema
import play.api.Configuration
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc.{BaseController, RawBuffer, Request, Result}
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.ValidationRules.ValidatedHeaders
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.Clock
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.TraderProfile
import cats.data.OptionT

trait ValidationRules { this: BaseController =>

  def uuidService: UuidService
  def clock: Clock
  def configuration: Configuration
  def schemaValidationService: SchemaValidationService
  def traderProfilesRepository: TraderProfileRepository

  implicit def ec: ExecutionContext

  private val expectedAuthHeader: String = configuration.get[String]("expected-auth-header")
  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")

  protected def validateAuthorization(implicit request: Request[_]): Either[Result, _] =
    request.headers.get("Authorization")
      .filter(_ == expectedAuthHeader)
      .toRight(Forbidden)

  protected def validateCorrelationId(implicit request: Request[_]): EitherNec[String, String] =
    request.headers.get("X-Correlation-Id").toRightNec("error: 001, message: Invalid Header")

  protected def validateForwardedHost(implicit request: Request[_]): EitherNec[String, String] =
    request.headers.get("X-Forwarded-Host").toRightNec("error: 005, message: Invalid Header")

  protected def validateDate(implicit request: Request[_]): EitherNec[String, _] =
    request.headers.get("Date").flatMap { dateString =>
      Try(rfc7231Formatter.parse(dateString)).toOption
    }.toRightNec("error: 002, message: Invalid Header")

  protected def validateContentType(implicit request: Request[_]): EitherNec[String, _] =
    request.headers.get("Content-Type")
      .filter(_ == "application/json")
      .toRightNec("error: 003, message: Invalid Header")

  protected def validateWriteHeaders(implicit request: Request[_]): Either[Result, ValidatedHeaders] = {
    (
      validateCorrelationId,
      validateForwardedHost,
      validateDate,
      validateContentType
    ).parMapN { (correlationId, forwardedHost, _, _) =>
      ValidatedHeaders(correlationId, forwardedHost)
    }.leftMap { errors =>
      badRequest(
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = errors.toList
      )
    }
  }

  protected def validateJsonBody(implicit request: Request[RawBuffer]): Either[Result, JsValue] = {
    request.body.asBytes().toRight(EntityTooLarge).flatMap { byteString =>
      Try(Json.parse(byteString.utf8String)).toOption.toRight {
        badRequest(
          errorCode = "400",
          errorMessage = "Invalid message : Bad Request",
          source = "Json Validation",
          detail = Seq.empty
        )
      }
    }
  }

  protected def validateRequestBody[A: Reads](schema: Schema)(implicit request: Request[RawBuffer]): Either[Result, A] = {
    validateJsonBody.flatMap { json =>

      val validationErrors = schemaValidationService.validate(schema, json)

      if (validationErrors.isEmpty) {
        Right(json.as[A])
      } else Left {
        badRequest(
          errorCode = "400",
          errorMessage = "Invalid message : Bad Request",
          source = "Json Validation",
          detail = validationErrors.map { error =>
            s"${error.key}: ${error.message}"
          }
        )
      }
    }
  }

  protected def getTraderProfile(eori: String)(implicit request: Request[_]): EitherT[Future, Result, TraderProfile] = {
    EitherT.fromOptionF(
      traderProfilesRepository.get(eori),
      badRequest(
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 007, message: Invalid Request Parameter"
        )
      )
    )
  }

  protected def badRequest(errorCode: String, errorMessage: String, source: String, detail: Seq[String])(implicit request: Request[_]): Result = {
    val correlationId = request.headers.get("X-Correlation-Id").getOrElse(uuidService.generate())
    BadRequest(Json.toJson(ErrorResponse(
      correlationId = correlationId,
      timestamp = clock.instant(),
      errorCode = errorCode,
      errorMessage = errorMessage,
      source = source,
      detail = detail
    ))).withHeaders("X-Correlation-ID" -> correlationId)
  }
}

object ValidationRules {

  final case class ValidatedHeaders(correlationId: String, forwardedHost: String)
}
