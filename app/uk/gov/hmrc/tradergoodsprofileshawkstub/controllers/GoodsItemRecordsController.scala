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
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.GoodsItemRecordsController.{ValidatedHeaders, ValidatedParams}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.{CreateGoodsItemRecordRequest, UpdateGoodsItemRecordRequest}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.{GetGoodsItemsResponse, Pagination}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant}
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
  private val defaultSize: Int = configuration.get[Int]("goods-item-records.default-size")
  private val maxSize: Int = configuration.get[Int]("goods-item-records.max-size")

  // Using `get` here as we want to throw an exception on startup if this can't be found
  private val createRecordSchema: Schema = schemaValidationService.createSchema("/schemas/tgp-create-record-request-v0.7.json").get
  private val updateRecordSchema: Schema = schemaValidationService.createSchema("/schemas/tgp-update-record-request-v0.2.json").get

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val iso8601Formatter = DateTimeFormatter.ISO_DATE_TIME

  def createRecord(): Action[RawBuffer] = Action.async(parse.raw) { implicit request =>

    val result = for {
      _                <- validateAuthorization(request)
      validatedHeaders <- validatePostHeaders(request)
      body             <- validateRequestBody[CreateGoodsItemRecordRequest](request, createRecordSchema)
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

  def getRecords(eori: String): Action[AnyContent] = Action.async { implicit request =>

    val result = for {
      _                <- validateAuthorization(request)
      validatedHeaders <- validateGetHeaders(request)
      validatedParams  <- validateGetParameters(request)
    } yield {

      val page = validatedParams.page.getOrElse(0)
      val size = validatedParams.size.getOrElse(defaultSize)

      goodsItemRecordRepository.get(eori, validatedParams.lastUpdatedDate, page, size).map { result =>
        val pagination = Pagination(totalRecords = result.totalCount.toInt, page, size)
        Ok(Json.toJson(GetGoodsItemsResponse(result.records, pagination)))
          .withHeaders(
            "X-Correlation-ID" -> validatedHeaders.correlationId,
            "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
            "Content-Type" -> "application/json"
          )
      }
    }

    result.leftMap(Future.successful).merge
  }

  def getRecord(eori: String, recordId: String): Action[AnyContent] = Action.async { implicit request =>

    val result = for {
      _                <- validateAuthorization(request)
      validatedHeaders <- validateGetHeaders(request)
    } yield {

      goodsItemRecordRepository.getById(eori, recordId).map { record =>
        val pagination = Pagination(totalRecords = record.size.toInt, page = 0, size = 1)
        Ok(Json.toJson(GetGoodsItemsResponse(record.toSeq, pagination)))
          .withHeaders(
            "X-Correlation-ID" -> validatedHeaders.correlationId,
            "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
            "Content-Type" -> "application/json"
          )
      }
    }

    result.leftMap(Future.successful).merge
  }

  def updateRecord(): Action[RawBuffer] = Action.async(parse.raw) { implicit request =>

    val result = for {
      _                <- validateAuthorization(request)
      validatedHeaders <- validatePostHeaders(request)
      body             <- validateRequestBody[UpdateGoodsItemRecordRequest](request, updateRecordSchema)
    } yield {

      goodsItemRecordRepository.update(body).map {

        _.map { goodsItemRecord =>
          Ok(goodsItemRecord.toCreateRecordResponse)
        }.getOrElse {
          BadRequest(Json.toJson(ErrorResponse(
            correlationId = validatedHeaders.correlationId,
            timestamp = clock.instant(),
            errorCode = "400",
            errorMessage = "Bad Request",
            source = "BACKEND",
            detail = Seq("error: XXX, message: Record does not exist") // What should this error code be?
          )))
        }.withHeaders(
          "X-Correlation-ID" -> validatedHeaders.correlationId,
          "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
          "Content-Type" -> "application/json"
        )
      }
    }

    result.leftMap(Future.successful).merge
  }

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

  private def validatePostHeaders(request: Request[_]): Either[Result, ValidatedHeaders] = {
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

  private def validateGetHeaders(request: Request[_]): Either[Result, ValidatedHeaders] = {
    (
      validateCorrelationId(request),
      validateForwardedHost(request),
      validateDate(request)
    ).parMapN { (correlationId, forwardedHost, _) =>
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

  private def validatePage(request: Request[_]): EitherNec[String, Option[Int]] =
    request.getQueryString("page").traverse { string =>
      string.toIntOption
        .filter(_ >= 0)
        .toRightNec("error: 029, message: Invalid Request Parameter")
    }

  private def validateSize(request: Request[_]): EitherNec[String, Option[Int]] =
    request.getQueryString("size").traverse { string =>
      string.toIntOption
        .filter(s => s >= 0 && s < maxSize)
        .toRightNec("error: 030, message: Invalid Request Parameter")
    }

  private def validateLastUpdatedDate(request: Request[_]): EitherNec[String, Option[Instant]] =
    request.getQueryString("lastUpdatedDate").traverse { string =>
      Try(Instant.from(iso8601Formatter.parse(string)))
        .toOption
        .toRightNec("error: 028, message: Invalid Request Parameter")
    }

  private def validateGetParameters(request: Request[_]): Either[Result, ValidatedParams] = {
    (
      validatePage(request),
      validateSize(request),
      validateLastUpdatedDate(request)
    ).parMapN(ValidatedParams).leftMap { errors =>

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

  private def validateJsonBody(request: Request[RawBuffer]): Either[Result, JsValue] = {
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

  private def validateRequestBody[A: Reads](request: Request[RawBuffer], schema: Schema): Either[Result, A] = {
    validateJsonBody(request).flatMap { json =>

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

object GoodsItemRecordsController {

  final case class ValidatedHeaders(correlationId: String, forwardedHost: String)

  final case class ValidatedParams(page: Option[Int], size: Option[Int], lastUpdatedDate: Option[Instant])
}
