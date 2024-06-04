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

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.PatchGoodsItemRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class GoodsItemTestSupportController @Inject()(
                                                override val controllerComponents: ControllerComponents,
                                                goodsItemRecordRepository: GoodsItemRecordRepository
                                              )(implicit ec: ExecutionContext) extends BackendBaseController {

  def patch(): Action[PatchGoodsItemRequest] = Action(parse.json[PatchGoodsItemRequest]).async { implicit request =>

    goodsItemRecordRepository
      .patch(request.body)
      .map(_.map(_ => Ok).getOrElse(NotFound))
  }
}
