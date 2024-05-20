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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.Assessment

import java.time.Instant

final case class UpdateGoodsItemRecordRequest(
                                               recordId: String,
                                               eori: String,
                                               actorId: String,
                                               traderRef: Option[String],
                                               comcode: Option[String],
                                               goodsDescription: Option[String],
                                               countryOfOrigin: Option[String],
                                               category: Option[Int],
                                               assessments: Option[Seq[Assessment]],
                                               supplementaryUnit: Option[String],
                                               measurementUnit: Option[String],
                                               comcodeEffectiveFromDate: Option[Instant],
                                               comcodeEffectiveToDate: Option[Instant]
                                             )

object UpdateGoodsItemRecordRequest {

  implicit lazy val format: OFormat[UpdateGoodsItemRecordRequest] = Json.format
}
