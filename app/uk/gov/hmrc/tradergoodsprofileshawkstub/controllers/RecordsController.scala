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

import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.{Inject, Singleton}

@Singleton
class RecordsController @Inject()(
                                               override val controllerComponents: ControllerComponents
                                             ) extends BackendBaseController {

  def createRecord(): Action[AnyContent] =
    Action(NotImplemented)

  def getRecords(eori: String): Action[AnyContent] =
    Action(NotImplemented)

  def getRecord(eori: String, recordId: String): Action[AnyContent] =
    Action(NotImplemented)

  def updateRecord(): Action[AnyContent] =
    Action(NotImplemented)

  def removeRecord(): Action[AnyContent] =
    Action(NotImplemented)
}
