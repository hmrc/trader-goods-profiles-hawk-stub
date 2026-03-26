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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.tradergoodsprofileshawkstub.models
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.StubbedGoodsItemMetadata._

import java.time.{Clock, Instant, ZoneOffset}
import play.api.libs.json._
import java.time.temporal.ChronoUnit
import java.time.Instant

class GoodsItemMetaDataSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

  "createStubbedGoodsItemMetadata" - {
    "must return the correct accreditation status" in {
      createStubbedGoodsItemMetadata(
        "GB777432814901",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.Requested
      createStubbedGoodsItemMetadata(
        "GB777432814902",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.InProgress
      createStubbedGoodsItemMetadata(
        "GB777432814903",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.InformationRequested
      createStubbedGoodsItemMetadata(
        "GB777432814904",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.Withdrawn
      createStubbedGoodsItemMetadata(
        "GB777432814905",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.Approved
      createStubbedGoodsItemMetadata(
        "GB777432814906",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.Rejected
      createStubbedGoodsItemMetadata(
        "GB111111111111",
        clock: Clock
      ).accreditationStatus mustBe models.AccreditationStatus.NotRequested

    }
  }
  "Json Format" - {
    val now   = Instant.parse("2026-03-20T11:00:00Z")
    val model = GoodsItemMetadata(
      accreditationStatus = AccreditationStatus.NotRequested,
      version = 1,
      active = true,
      locked = false,
      toReview = false,
      declarable = Some("Ready"),
      reviewReason = None,
      srcSystemName = "MDTP",
      createdDateTime = Instant.parse("2026-03-20T11:00:00Z"),
      updatedDateTime = Instant.parse("2026-03-20T11:00:00Z")
    )

    "must serialise and deserialise using the standard format" in {
      val json = Json.toJson(model)(GoodsItemMetadata.format)

      (json \ "accreditationStatus").as[String] mustBe "Not Requested"
      (json \ "version").as[Int] mustBe 1
      (json \ "createdDateTime").as[Instant] mustBe now

      json.as[GoodsItemMetadata](GoodsItemMetadata.format) mustBe model
    }

    "must serialise and deserialise using mongoFormat (handling Instant via MongoJavatimeFormats)" in {
      // This triggers the lazy val mongoFormat and the internal instantFormat (Line 40-43)
      implicit val mongoFormat: OFormat[GoodsItemMetadata] = GoodsItemMetadata.mongoFormat

      val json = Json.toJson(model)

      // Verify the field exists and can be read back
      (json \ "srcSystemName").as[String] mustBe "MDTP"

      val result = json.as[GoodsItemMetadata]
      result mustBe model
      result.updatedDateTime mustBe now
    }
  }
}
