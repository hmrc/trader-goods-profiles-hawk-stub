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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.TraderProfile

case class TraderProfileResponse(
  eori: String,
  actorId: String,
  ukimsNumber: Option[String],
  nirmsNumber: Option[String],
  niphlNumber: Option[String]
)

object TraderProfileResponse {
  implicit val format: OFormat[TraderProfileResponse] = Json.format[TraderProfileResponse]

  def createFrom(traderProfile: TraderProfile): TraderProfileResponse =
    TraderProfileResponse(
      eori = traderProfile.eori,
      actorId = traderProfile.actorId,
      ukimsNumber = traderProfile.ukimsNumber,
      nirmsNumber = traderProfile.nirmsNumber,
      niphlNumber = traderProfile.niphlNumber
    )
}
