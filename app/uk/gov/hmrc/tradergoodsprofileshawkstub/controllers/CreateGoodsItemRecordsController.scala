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

import cats.data.EitherT
import org.everit.json.schema.Schema
import play.api.Configuration
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.actions.HeaderPropagationFilter
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateGoodsItemRecordRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository.DuplicateEoriAndTraderRefException
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.{GoodsItemRecordRepository, TraderProfileRepository}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateGoodsItemRecordsController @Inject() (
  override val controllerComponents: ControllerComponents,
  override val traderProfilesRepository: TraderProfileRepository,
  override val uuidService: UuidService,
  override val clock: Clock,
  override val configuration: Configuration,
  override val schemaValidationService: SchemaValidationService,
  goodsItemRecordRepository: GoodsItemRecordRepository,
  headersFilter: HeaderPropagationFilter
)(implicit override val ec: ExecutionContext)
    extends BackendBaseController
    with ValidationRules {

  // Using `get` here as we want to throw an exception on startup if this can't be found
  private val createRecordSchema: Schema =
    schemaValidationService.createSchema("/schemas/tgp-create-record-request-v0.7.json").get

  def createRecord(): Action[RawBuffer] = (Action andThen headersFilter).async(parse.raw) { implicit request =>
    val result = for {
      _       <- EitherT.fromEither[Future](validateAuthorization)
      _       <- EitherT.fromEither[Future](validateWriteHeaders)
      body    <- EitherT.fromEither[Future](validateRequestBody[CreateGoodsItemRecordRequest](createRecordSchema))
      profile <- getTraderProfile(body.eori)
    } yield goodsItemRecordRepository
      .insert(body)
      .map { goodsItemRecord =>
        Created(goodsItemRecord.toCreateRecordResponse(profile, clock.instant()))
      }
      .recover { case DuplicateEoriAndTraderRefException =>
        badRequest(
          errorCode = "400",
          errorMessage = "Bad Request",
          source = "BACKEND",
          detail = Seq("error: 010, message: Invalid Request Parameter")
        )
      }

    result.leftMap(Future.successful).merge.flatten
  }
}
