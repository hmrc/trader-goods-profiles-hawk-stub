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

class GetGoodsItemRecordsResultSpec extends AnyFreeSpec with Matchers {

  "GetGoodsItemRecordsResult mongoFormat" - {

    "must serialise and deserialise correctly using mongoFormat" in {

      // 1. Create the nested GoodsItem
      val goodsItem = GoodsItem(
        eori = "GB123456789",
        actorId = "actorId",
        traderRef = "traderRef",
        comcode = "comcode",
        goodsDescription = "description",
        countryOfOrigin = "GB",
        category = Some(Category.Standard),
        assessments = None,
        supplementaryUnit = None,
        measurementUnit = None,
        comcodeEffectiveFromDate = Instant.now(),
        comcodeEffectiveToDate = None
      )

      // 2. Create the nested Metadata
      val metadata = GoodsItemMetadata(
        accreditationStatus = AccreditationStatus.NotRequested,
        version = 1,
        active = true,
        locked = false,
        toReview = false,
        declarable = Some("Ready"),
        reviewReason = None,
        srcSystemName = "MDTP",
        createdDateTime = Instant.now(),
        updatedDateTime = Instant.now()
      )

      // 3. Create the Record (passing the nested objects)
      val record = GoodsItemRecord(
        recordId = "record-id-123",
        goodsItem = goodsItem,
        metadata = metadata
      )

      // 4. Create the Result (using 'totalCount' and 'records' as per compiler error)
      val model = GetGoodsItemRecordsResult(
        totalCount = 1L,
        records = Seq(record)
      )

      // Trigger the lazy val mongoFormat
      implicit val format: OFormat[GetGoodsItemRecordsResult] = GetGoodsItemRecordsResult.mongoFormat

      val json = Json.toJson(model)

      // 5. Assertions (using correct field names)
      (json \ "totalCount").as[Long] mustBe 1L
      (json \ "records").as[JsArray].value.size mustBe 1

      // 6. Test Round-trip (Deserialization)
      val result = json.as[GetGoodsItemRecordsResult]
      result.totalCount mustBe 1L
      result.records.head.goodsItem.eori mustBe "GB123456789"
    }
  }
}
