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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models.response

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.TraderProfileResponse
import play.api.libs.json.{JsSuccess, Json}

class TraderProfileResponseSpec extends AnyFreeSpec with Matchers {

  "TraderProfileResponse JSON Format" - {

    "must serialise to JSON correctly" in {
      val model = TraderProfileResponse(
        eori = "GB123456789000",
        actorId = "actor-123",
        ukimsNumber = Some("UKIMS123"),
        nirmsNumber = Some("NIRMS456"),
        niphlNumber = None
      )

      val json = Json.toJson(model)

      (json \ "eori").as[String] mustBe "GB123456789000"
      (json \ "actorId").as[String] mustBe "actor-123"
      (json \ "ukimsNumber").as[String] mustBe "UKIMS123"
      (json \ "nirmsNumber").as[String] mustBe "NIRMS456"
      (json \ "niphlNumber").toOption mustBe None
    }

    "must deserialise from JSON correctly" in {
      val json = Json.obj(
        "eori"        -> "GB123456789000",
        "actorId"     -> "actor-123",
        "ukimsNumber" -> "UKIMS123",
        "nirmsNumber" -> "NIRMS456"
        // niphlNumber is missing, which handles the Option[String] = None case
      )

      val result = json.validate[TraderProfileResponse]

      result mustBe a[JsSuccess[_]]
      result.get.eori mustBe "GB123456789000"
      result.get.niphlNumber mustBe None
    }
  }
}
