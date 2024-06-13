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

import play.api.libs.json.{JsNull, JsObject, Json, OFormat}

import java.time.Instant

final case class GoodsItemRecord(
                                  recordId: String,
                                  goodsItem: GoodsItem,
                                  metadata: GoodsItemMetadata
                                ) {

  def toCreateRecordResponse(now: Instant): JsObject = Json.toJsObject(Json.obj(
    "recordId" -> recordId,
    "eori" -> goodsItem.eori,
    "actorId" -> goodsItem.actorId,
    "traderRef" -> goodsItem.traderRef,
    "comcode" -> goodsItem.comcode,
    "accreditationStatus" -> metadata.accreditationStatus,
    "goodsDescription" -> goodsItem.goodsDescription,
    "countryOfOrigin" -> goodsItem.countryOfOrigin,
    "category" -> goodsItem.category,
    "assessments" -> goodsItem.assessments,
    "supplementaryUnit" -> goodsItem.supplementaryUnit,
    "measurementUnit" -> goodsItem.measurementUnit,
    "comcodeEffectiveFromDate" -> goodsItem.comcodeEffectiveFromDate,
    "comcodeEffectiveToDate" -> goodsItem.comcodeEffectiveToDate,
    "version" -> metadata.version,
    "active" -> metadata.active,
    "toReview" -> metadata.toReview,
    "reviewReason" -> metadata.reviewReason,
    "declarable" -> declarable(now),
    "ukimsNumber" -> metadata.ukimsNumber,
    "nirmsNumber" -> metadata.nirmsNumber,
    "niphlNumber" -> metadata.niphlNumber,
    "createdDateTime" -> metadata.createdDateTime,
    "updatedDateTime" -> metadata.updatedDateTime
  ).fields.filterNot(_._2 == JsNull).toMap)

  def toGetRecordResponse(now: Instant): JsObject = Json.toJsObject(Json.obj(
    "recordId" -> recordId,
    "eori" -> goodsItem.eori,
    "actorId" -> goodsItem.actorId,
    "traderRef" -> goodsItem.traderRef,
    "comcode" -> goodsItem.comcode,
    "accreditationStatus" -> metadata.accreditationStatus,
    "goodsDescription" -> goodsItem.goodsDescription,
    "countryOfOrigin" -> goodsItem.countryOfOrigin,
    "category" -> goodsItem.category,
    "assessments" -> goodsItem.assessments,
    "supplementaryUnit" -> goodsItem.supplementaryUnit,
    "measurementUnit" -> goodsItem.measurementUnit,
    "comcodeEffectiveFromDate" -> goodsItem.comcodeEffectiveFromDate,
    "comcodeEffectiveToDate" -> goodsItem.comcodeEffectiveToDate,
    "version" -> metadata.version,
    "active" -> metadata.active,
    "toReview" -> metadata.toReview,
    "reviewReason" -> metadata.reviewReason,
    "declarable" -> declarable(now),
    "ukimsNumber" -> metadata.ukimsNumber,
    "nirmsNumber" -> metadata.nirmsNumber,
    "niphlNumber" -> metadata.niphlNumber,
    "locked" -> metadata.locked,
    "srcSystemName" -> metadata.srcSystemName,
    "createdDateTime" -> metadata.createdDateTime,
    "updatedDateTime" -> metadata.updatedDateTime
  ).fields.filterNot(_._2 == JsNull).toMap)

  def declarable(now: Instant): Declarable =
    if (metadata.active && !metadata.toReview && comcodeInEffect(now)) {
      goodsItem.category match {
        case Category.Standard =>
          if (goodsItem.comcode.length >= 6) Declarable.ImmiReady else Declarable.NotReady
        case Category.Controlled =>
          if (goodsItem.comcode.length >= 8) Declarable.ImmiReady else Declarable.NotReady
        case Category.Excluded =>
          Declarable.ImmiNotReady
      }
    } else {
      Declarable.NotReady
    }

  private def comcodeInEffect(now: Instant): Boolean =
    !now.isBefore(goodsItem.comcodeEffectiveFromDate) && goodsItem.comcodeEffectiveToDate.forall(d => !now.isAfter(d))
}

object GoodsItemRecord {

  implicit lazy val format: OFormat[GoodsItemRecord] = Json.format

  lazy val mongoFormat: OFormat[GoodsItemRecord] = {
    implicit val goodsItemFormat: OFormat[GoodsItem] = GoodsItem.mongoFormat
    implicit val goodsItemMetadataFormat: OFormat[GoodsItemMetadata] = GoodsItemMetadata.mongoFormat
    Json.format
  }
}
