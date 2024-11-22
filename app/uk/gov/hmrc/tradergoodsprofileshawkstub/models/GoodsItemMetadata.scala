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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import java.time.{Clock, Instant}

final case class GoodsItemMetadata(
  accreditationStatus: AccreditationStatus,
  version: Int,
  active: Boolean,
  locked: Boolean,
  toReview: Boolean,
  declarable: Option[String],
  reviewReason: Option[String],
  srcSystemName: String,
  createdDateTime: Instant,
  updatedDateTime: Instant
)

object GoodsItemMetadata {

  implicit lazy val format: OFormat[GoodsItemMetadata] = Json.format

  lazy val mongoFormat: OFormat[GoodsItemMetadata] = {
    implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    Json.format
  }
}

object StubbedGoodsItemMetadata {

  def createStubbedGoodsItemMetadata(eori: String, clock: Clock): GoodsItemMetadata =
    eori match {
      case "GB777432814901" => requestedGoodsItemMetadata(clock)
      case "GB777432814902" => inProgressGoodsItemMetadata(clock)
      case "GB777432814903" => informationRequestedGoodsItemMetadata(clock)
      case "GB777432814904" => withdrawnGoodsItemMetadata(clock)
      case "GB777432814905" => approvedGoodsItemMetadata(clock)
      case "GB777432814906" => rejectedGoodsItemMetadata(clock)
      case _                => notRequestedGoodsItemMetadata(clock)

    }

  def notRequestedGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.NotRequested,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )

  def requestedGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.Requested,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )

  def inProgressGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.InProgress,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )

  def informationRequestedGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.InformationRequested,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )

  def withdrawnGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.Withdrawn,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )

  def approvedGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.Approved,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )

  def rejectedGoodsItemMetadata(clock: Clock) = GoodsItemMetadata(
    accreditationStatus = AccreditationStatus.Rejected,
    version = 1,
    active = true,
    locked = false,
    toReview = false,
    reviewReason = None,
    declarable = None,
    srcSystemName = "MDTP",
    createdDateTime = clock.instant(),
    updatedDateTime = clock.instant()
  )
}
