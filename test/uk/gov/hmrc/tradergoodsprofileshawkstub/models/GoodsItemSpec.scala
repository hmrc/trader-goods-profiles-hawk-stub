/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json._
import java.time.Instant

class GoodsItemSpec extends AnyFreeSpec with Matchers {

  // Create a base model to use in tests
  val model = GoodsItem(
    eori = "GB123456789",
    actorId = "actorId",
    traderRef = "traderRef",
    comcode = "12345678",
    goodsDescription = "Test Description",
    countryOfOrigin = "GB",
    category = Some(Category.Standard),
    assessments = None,
    supplementaryUnit = Some(BigDecimal(100.0)),
    measurementUnit = Some("KGM"),
    comcodeEffectiveFromDate = Instant.parse("2026-03-20T10:00:00Z"),
    comcodeEffectiveToDate = None
  )

  "GoodsItem" - {

    "must serialise and deserialise using the standard format" in {
      val json = Json.toJson(model)(GoodsItem.format)

      (json \ "eori").as[String] mustBe "GB123456789"
      (json \ "comcodeEffectiveFromDate").as[Instant] mustBe model.comcodeEffectiveFromDate

      json.as[GoodsItem](GoodsItem.format) mustBe model
    }

    "must serialise and deserialise using mongoFormat (handling Instant via MongoJavatimeFormats)" in {
      // This triggers the lazy val mongoFormat and the internal instantFormat
      implicit val mongoFormat: OFormat[GoodsItem] = GoodsItem.mongoFormat

      val json = Json.toJson(model)

      // MongoJavatimeFormats typically serialises Instant as an object with a $date field
      // but we mainly care that the round-trip works and the coverage is triggered
      (json \ "eori").as[String] mustBe "GB123456789"

      val result = json.as[GoodsItem]
      result mustBe model
      result.comcodeEffectiveFromDate mustBe model.comcodeEffectiveFromDate
    }
  }
}
