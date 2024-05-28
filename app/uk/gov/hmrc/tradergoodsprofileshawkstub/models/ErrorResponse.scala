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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.time.Instant

final case class ErrorResponse(
                                correlationId: String,
                                timestamp: Instant,
                                errorCode: String,
                                errorMessage: String,
                                source: String,
                                detail: Seq[String]
                              )

object ErrorResponse {

  implicit lazy val writes: OWrites[ErrorResponse] =
    (
      (__ \ "errorDetail" \ "correlationId").write[String] ~
      (__ \ "errorDetail" \ "timestamp").write[Instant] ~
      (__ \ "errorDetail" \ "errorCode").write[String] ~
      (__ \ "errorDetail" \ "errorMessage").write[String] ~
      (__ \ "errorDetail" \ "source").write[String] ~
      (__ \ "errorDetail" \ "sourceFaultDetail" \ "detail").write[Seq[String]]
    )(unlift(ErrorResponse.unapply))
}