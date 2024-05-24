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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

import java.time.{Clock, Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import java.util.UUID

class GoodsItemRecordSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)


  "toCreateRecordResponse" - {

    "must return the expected json for a create record response" - {

      "when all optional fields are included" in {

        val fullGoodsItemRecord = GoodsItemRecord(
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
                assessmentId = Some("assessmentId"),
                primaryCategory = Some(2),
                condition = Some(Condition(
                  `type` = Some("type"),
                  conditionId = Some("conditionId"),
                  conditionDescription = Some("conditionDescription"),
                  conditionTraderText = Some("conditionTraderText")
                ))
              )
            ),
            supplementaryUnit = Some(BigDecimal(2.5)),
            measurementUnit = Some("measurementUnit"),
            comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
            comcodeEffectiveToDate = Some(clock.instant().plus(1, ChronoUnit.DAYS))
          ),
          metadata = GoodsItemMetadata(
            accreditationStatus = AccreditationStatus.NotRequested,
            version = 1,
            active = true,
            locked = false,
            toReview = true,
            reviewReason = Some("reviewReason"),
            declarable = "declarable",
            ukimsNumber = Some("ukims"),
            nirmsNumber = Some("nirms"),
            niphlNumber = Some("niphl"),
            srcSystemName = "MDTP",
            updatedDateTime = clock.instant(),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val expectedJson = Json.obj(
          "recordId" -> fullGoodsItemRecord.recordId,
          "eori" -> fullGoodsItemRecord.goodsItem.eori,
          "actorId" -> fullGoodsItemRecord.goodsItem.actorId,
          "traderRef" -> fullGoodsItemRecord.goodsItem.traderRef,
          "comcode" -> fullGoodsItemRecord.goodsItem.comcode,
          "accreditationStatus" -> fullGoodsItemRecord.metadata.accreditationStatus,
          "goodsDescription" -> fullGoodsItemRecord.goodsItem.goodsDescription,
          "countryOfOrigin" -> fullGoodsItemRecord.goodsItem.countryOfOrigin,
          "category" -> fullGoodsItemRecord.goodsItem.category,
          "assessments" -> fullGoodsItemRecord.goodsItem.assessments,
          "supplementaryUnit" -> fullGoodsItemRecord.goodsItem.supplementaryUnit,
          "measurementUnit" -> fullGoodsItemRecord.goodsItem.measurementUnit,
          "comcodeEffectiveFromDate" -> fullGoodsItemRecord.goodsItem.comcodeEffectiveFromDate,
          "comcodeEffectiveToDate" -> fullGoodsItemRecord.goodsItem.comcodeEffectiveToDate,
          "version" -> fullGoodsItemRecord.metadata.version,
          "active" -> fullGoodsItemRecord.metadata.active,
          "toReview" -> fullGoodsItemRecord.metadata.toReview,
          "reviewReason" -> fullGoodsItemRecord.metadata.reviewReason,
          "declarable" -> fullGoodsItemRecord.metadata.declarable,
          "ukimsNumber" -> fullGoodsItemRecord.metadata.ukimsNumber,
          "nirmsNumber" -> fullGoodsItemRecord.metadata.nirmsNumber,
          "niphlNumber" -> fullGoodsItemRecord.metadata.niphlNumber,
          "createdDateTime" -> fullGoodsItemRecord.metadata.createdDateTime,
          "updatedDateTime" -> fullGoodsItemRecord.metadata.updatedDateTime
        )

        fullGoodsItemRecord.toCreateRecordResponse mustEqual expectedJson
      }

      "when no optional fields are included" in {

        val minimumGoodsItemRecord = GoodsItemRecord(
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

        val expectedJson = Json.obj(
          "recordId" -> minimumGoodsItemRecord.recordId,
          "eori" -> minimumGoodsItemRecord.goodsItem.eori,
          "actorId" -> minimumGoodsItemRecord.goodsItem.actorId,
          "traderRef" -> minimumGoodsItemRecord.goodsItem.traderRef,
          "comcode" -> minimumGoodsItemRecord.goodsItem.comcode,
          "accreditationStatus" -> minimumGoodsItemRecord.metadata.accreditationStatus,
          "goodsDescription" -> minimumGoodsItemRecord.goodsItem.goodsDescription,
          "countryOfOrigin" -> minimumGoodsItemRecord.goodsItem.countryOfOrigin,
          "category" -> minimumGoodsItemRecord.goodsItem.category,
          "assessments" -> Json.arr(Json.obj()),
          "comcodeEffectiveFromDate" -> minimumGoodsItemRecord.goodsItem.comcodeEffectiveFromDate,
          "version" -> minimumGoodsItemRecord.metadata.version,
          "active" -> minimumGoodsItemRecord.metadata.active,
          "toReview" -> minimumGoodsItemRecord.metadata.toReview,
          "declarable" -> minimumGoodsItemRecord.metadata.declarable,
          "createdDateTime" -> minimumGoodsItemRecord.metadata.createdDateTime,
          "updatedDateTime" -> minimumGoodsItemRecord.metadata.updatedDateTime
        )

        minimumGoodsItemRecord.toCreateRecordResponse mustEqual expectedJson
      }
    }
  }

}
