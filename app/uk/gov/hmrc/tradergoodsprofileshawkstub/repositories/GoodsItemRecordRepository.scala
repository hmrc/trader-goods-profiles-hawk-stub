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

import org.mongodb.scala.model._
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateGoodsItemRecordRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.{Clock, Instant}
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
  indexes = GoodsItemRecordRepository.indexes(configuration),
  extraCodecs = Seq(
    Codecs.playFormatCodec(GetGoodsItemRecordsResult.mongoFormat)
  )
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

  def get(eori: String, lastUpdated: Option[Instant] = None, page: Int = 0, size: Int = 100000): Future[GetGoodsItemRecordsResult] = Mdc.preservingMdc {
    collection.aggregate[GetGoodsItemRecordsResult](Seq(
      Aggregates.`match`(
        Filters.and(
          Filters.eq("goodsItem.eori", eori),
          lastUpdated.map(Filters.gte("metadata.updatedDateTime", _)).getOrElse(Filters.empty())
        )
      ),
      Aggregates.sort(Sorts.ascending("metadata.updatedDateTime")),
      Aggregates.facet(
        Facet("totalCount", Aggregates.count("count")),
        Facet("records", Aggregates.skip(page * size), Aggregates.limit(size))
      ),
      Aggregates.unwind("$totalCount"),
      Aggregates.project(
        Json.obj(
          "totalCount" -> "$totalCount.count",
          "records" -> 1
        ).toDocument()
      )
    )).head()
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
        Indexes.ascending("goodsItem.eori"),
        IndexOptions()
          .name("eori_idx")
      ),
      IndexModel(
        Indexes.ascending("eori", "metadata.updatedDateTime"),
        IndexOptions()
          .name("eori_updatedDateTime_idx")
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