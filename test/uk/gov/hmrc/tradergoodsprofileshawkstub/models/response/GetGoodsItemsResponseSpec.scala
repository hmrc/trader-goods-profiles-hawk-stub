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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models.response

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.{GetGoodsItemsResponse, Pagination}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.{AccreditationStatus, Assessment, GoodsItem, GoodsItemMetadata, GoodsItemRecord}

import java.time.{Clock, Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import java.util.UUID

class GetGoodsItemsResponseSpec extends AnyFreeSpec with Matchers {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

  "writes" - {

    "must write to json" in {

      val goodsItemRecord = GoodsItemRecord(
        recordId = UUID.randomUUID().toString,
        goodsItem = GoodsItem(
          eori = "eori",
          actorId = "actorId",
          traderRef = "traderRef",
          comcode = "comcode",
          goodsDescription = "goodsDescription",
          countryOfOrigin = "countryOfOrigin",
          category = 1,
          assessments = Seq(
            Assessment(
              assessmentId = None,
              primaryCategory = None,
              condition = None
            )
          ),
          supplementaryUnit = None,
          measurementUnit = None,
          comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
          comcodeEffectiveToDate = None
        ),
        metadata = GoodsItemMetadata(
          accreditationStatus = AccreditationStatus.NotRequested,
          version = 1,
          active = true,
          locked = false,
          toReview = false,
          reviewReason = None,
          declarable = "declarable",
          ukimsNumber = None,
          nirmsNumber = None,
          niphlNumber = None,
          srcSystemName = "MDTP",
          updatedDateTime = clock.instant(),
          createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
        )
      )

      val response = GetGoodsItemsResponse(
        goodsItemRecords = Seq(goodsItemRecord),
        pagination = Pagination(
          totalRecords = 2,
          currentPage = 1,
          totalPages = 2,
          nextPage = Some(2),
          previousPage = None
        )
      )

      val expectedJson = Json.obj(
        "goodsItemRecords" -> Json.arr(goodsItemRecord.toGetRecordResponse),
        "pagination" -> Json.obj(
          "totalRecords" -> 2,
          "currentPage" -> 1,
          "totalPages" -> 2,
          "nextPage" -> 2,
          "previousPage" -> JsNull
        )
      )

      Json.toJsObject(response) mustEqual expectedJson
    }
  }
}
