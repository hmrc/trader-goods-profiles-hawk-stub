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
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.{Assessment, Category}

import java.time.Instant

final case class CreateGoodsItemRecordRequest(
                                               eori: String,
                                               actorId: String,
                                               traderRef: String,
                                               comcode: String,
                                               goodsDescription: String,
                                               countryOfOrigin: String,
                                               category: Category,
                                               assessments: Option[Seq[Assessment]],
                                               supplementaryUnit: Option[BigDecimal],
                                               measurementUnit: Option[String],
                                               comcodeEffectiveFromDate: Instant,
                                               comcodeEffectiveToDate: Option[Instant]
                                             )

object CreateGoodsItemRecordRequest {

  implicit lazy val format: OFormat[CreateGoodsItemRecordRequest] = Json.format
}
