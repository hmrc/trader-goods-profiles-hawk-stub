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

package uk.gov.hmrc.tradergoodsprofileshawkstub.repositories

import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateGoodsItemRecordRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.{AccreditationStatus, GoodsItem, GoodsItemMetadata, GoodsItemRecord}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GoodsItemRecordRepository @Inject() (
                                            mongoComponent: MongoComponent,
                                            configuration: Configuration,
                                            uuidService: UuidService,
                                            clock: Clock
                                          )(implicit ec: ExecutionContext) extends PlayMongoRepository[GoodsItemRecord](
  mongoComponent = mongoComponent,
  collectionName = "goodsItemRecords",
  domainFormat = GoodsItemRecord.mongoFormat,
  indexes = GoodsItemRecordRepository.indexes(configuration)
) {

  def insert(request: CreateGoodsItemRecordRequest): Future[GoodsItemRecord] = Mdc.preservingMdc {

    val goodsItemRecord = GoodsItemRecord(
      recordId = uuidService.generate(),
      goodsItem = GoodsItem(
        eori = request.eori,
        actorId = request.actorId,
        traderRef = request.traderRef,
        comcode = request.comcode,
        accreditationStatus = AccreditationStatus.NotRequested,
        goodsDescription = request.goodsDescription,
        countryOfOrigin = request.countryOfOrigin,
        category = request.category,
        assessments = request.assessments.getOrElse(Seq.empty), // TODO test?
        supplementaryUnit = request.supplementaryUnit,
        measurementUnit = request.measurementUnit,
        comcodeEffectiveFromDate = request.comcodeEffectiveFromDate,
        comcodeEffectiveToDate = request.comcodeEffectiveToDate
      ),
      metadata = GoodsItemMetadata(
       version = 1,
        active = true,
        locked = false,
        toReview = false,
        reviewReason = None,
        declarable = "declarable", // TODO what should this be?
        ukimsNumber = None,
        nirmsNumber = None,
        niphlNumber = None,
        srcSystemName = "MDTP",
        createdDateTime = clock.instant(),
        updatedDateTime = clock.instant()
      )
    )

    collection.insertOne(goodsItemRecord)
      .toFuture()
      .map(_ => goodsItemRecord)
  }

  def getById(eori: String, recordId: String): Future[Option[GoodsItemRecord]] = Mdc.preservingMdc {
    collection.find(
      Filters.and(
        Filters.eq("recordId", recordId),
        Filters.eq("goodsItem.eori", eori)
      )
    ).headOption()
  }
}

object GoodsItemRecordRepository {

  private def indexes(configuration: Configuration): Seq[IndexModel] = {

    val ttl = configuration.get[FiniteDuration]("goods-item-records.ttl")

    Seq(
      IndexModel(
        Indexes.ascending("recordId"),
        IndexOptions()
          .name("recordId_idx")
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("recordId", "goodsItem.eori"),
        IndexOptions()
          .name("recordId_eori_idx")
      ),
      IndexModel(
        Indexes.ascending("metadata.updatedDateTime"),
        IndexOptions()
          .name("updatedDateTime_ttl_idx")
          .expireAfter(ttl.toSeconds, TimeUnit.SECONDS)
      )
    )
  }
}