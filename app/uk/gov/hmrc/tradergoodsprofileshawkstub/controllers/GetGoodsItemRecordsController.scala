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
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.GetGoodsItemRecordsController.ValidatedParams
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.actions.HeaderPropagationFilter
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.{GetGoodsItemsResponse, Pagination}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.{GoodsItemRecordRepository, TraderProfileRepository}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class GetGoodsItemRecordsController @Inject()(
                                            override val controllerComponents: ControllerComponents,
                                            override val traderProfilesRepository: TraderProfileRepository,
                                            override val uuidService: UuidService,
                                            override val clock: Clock,
                                            override val configuration: Configuration,
                                            override val schemaValidationService: SchemaValidationService,
                                            goodsItemRecordRepository: GoodsItemRecordRepository,
                                            headersFilter: HeaderPropagationFilter
                                          )(implicit override val ec: ExecutionContext) extends BackendBaseController with ValidationRules {

  private val defaultSize: Int = configuration.get[Int]("goods-item-records.default-size")
  private val maxSize: Int = configuration.get[Int]("goods-item-records.max-size")

  private val iso8601Formatter = DateTimeFormatter.ISO_DATE_TIME

  def getRecords(eori: String): Action[AnyContent] = (Action andThen headersFilter).async { implicit request =>
    val result = for {
      _                <- validateAuthorization
      validatedHeaders <- validateHeaders
      validatedParams  <- validateParameters
    } yield {

      val page = validatedParams.page.getOrElse(0)
      val size = validatedParams.size.getOrElse(defaultSize)

      goodsItemRecordRepository.get(eori, validatedParams.lastUpdatedDate, page, size).map { result =>
        val pagination = Pagination(totalRecords = result.totalCount.toInt, page, size)
        Ok(Json.toJson(GetGoodsItemsResponse(result.records, pagination))(GetGoodsItemsResponse.writes(clock.instant())))
          .withHeaders(
            "X-Correlation-ID" -> validatedHeaders.correlationId,
            "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
            "Content-Type" -> "application/json"
          )
      }
    }

    result.leftMap(Future.successful).merge
  }

  def getRecord(eori: String, recordId: String): Action[AnyContent] = (Action andThen headersFilter).async { implicit request =>
    val result = for {
      _                <- validateAuthorization
      validatedHeaders <- validateHeaders
    } yield {
      goodsItemRecordRepository.getById(eori, recordId).map { record =>
        val pagination = Pagination(totalRecords = record.size.toInt, page = 0, size = 1)
        Ok(Json.toJson(GetGoodsItemsResponse(record.toSeq, pagination))(GetGoodsItemsResponse.writes(clock.instant())))
          .withHeaders(
            "X-Correlation-ID" -> validatedHeaders.correlationId,
            "X-Forwarded-Host" -> validatedHeaders.forwardedHost,
            "Content-Type" -> "application/json"
          )
      }
    }

    result.leftMap(Future.successful).merge
  }

  private def validatePage(implicit request: Request[_]): EitherNec[String, Option[Int]] =
    request.getQueryString("page").traverse { string =>
      string.toIntOption
        .filter(_ >= 0)
        .toRightNec("error: 029, message: Invalid Request Parameter")
    }

  private def validateSize(implicit request: Request[_]): EitherNec[String, Option[Int]] =
    request.getQueryString("size").traverse { string =>
      string.toIntOption
        .filter(s => s >= 0 && s < maxSize)
        .toRightNec("error: 030, message: Invalid Request Parameter")
    }

  private def validateLastUpdatedDate(implicit request: Request[_]): EitherNec[String, Option[Instant]] =
    request.getQueryString("lastUpdatedDate").traverse { string =>
      Try(Instant.from(iso8601Formatter.parse(string)))
        .toOption
        .toRightNec("error: 028, message: Invalid Request Parameter")
    }

  private def validateParameters(implicit request: Request[_]): Either[Result, ValidatedParams] = {
    (
      validatePage,
      validateSize,
      validateLastUpdatedDate
    ).parMapN(ValidatedParams).leftMap { errors =>
      badRequest(
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = errors.toList
      )
    }
  }
}

object GetGoodsItemRecordsController {

  final case class ValidatedParams(page: Option[Int], size: Option[Int], lastUpdatedDate: Option[Instant])
}
