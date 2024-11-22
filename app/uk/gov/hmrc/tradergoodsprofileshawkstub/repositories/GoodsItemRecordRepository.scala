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

import org.apache.pekko.Done
import org.mongodb.scala.model._
import org.mongodb.scala.{ClientSession, MongoCommandException, MongoException, MongoWriteException}
import play.api.Configuration
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.StubbedGoodsItemMetadata.createStubbedGoodsItemMetadata
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.{CreateGoodsItemRecordRequest, PatchGoodsItemRecordRequest, PatchGoodsItemRequest, RemoveGoodsItemRecordRequest, UpdateGoodsItemRecordRequest}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository.{DuplicateEoriAndTraderRefException, RecordInactiveException, RecordLockedException}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class GoodsItemRecordRepository @Inject() (
  override val mongoComponent: MongoComponent,
  configuration: Configuration,
  uuidService: UuidService,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[GoodsItemRecord](
      mongoComponent = mongoComponent,
      collectionName = "goodsItemRecords",
      domainFormat = GoodsItemRecord.mongoFormat,
      indexes = GoodsItemRecordRepository.indexes(configuration),
      replaceIndexes = true,
      extraCodecs = Seq(
        Codecs.playFormatCodec(GetGoodsItemRecordsResult.mongoFormat),
        Codecs.playFormatCodec(Assessment.format),
        Codecs.playFormatCodec(implicitly[Format[BigDecimal]])
      ) ++ Codecs.playFormatSumCodecs(implicitly[Format[Category]])
    )
    with Transactions {

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def insert(request: CreateGoodsItemRecordRequest): Future[GoodsItemRecord] = Mdc.preservingMdc {

    val goodsItemRecord = GoodsItemRecord(
      recordId = uuidService.generate(),
      goodsItem = GoodsItem(
        eori = request.eori,
        actorId = request.actorId,
        traderRef = request.traderRef,
        comcode = request.comcode,
        goodsDescription = request.goodsDescription,
        countryOfOrigin = request.countryOfOrigin,
        category = request.category,
        assessments = request.assessments,
        supplementaryUnit = request.supplementaryUnit,
        measurementUnit = request.measurementUnit,
        comcodeEffectiveFromDate = request.comcodeEffectiveFromDate,
        comcodeEffectiveToDate = request.comcodeEffectiveToDate
      ),
      metadata = createStubbedGoodsItemMetadata(request.eori, clock)
    )

    collection
      .insertOne(goodsItemRecord)
      .toFuture()
      .map(_ => goodsItemRecord)
      .recoverWith {
        case e: MongoWriteException if isDuplicateKeyException(e) =>
          Future.failed(DuplicateEoriAndTraderRefException)
      }
  }

  def getById(eori: String, recordId: String): Future[Option[GoodsItemRecord]] = Mdc.preservingMdc {
    collection
      .find(
        Filters.and(
          Filters.eq("recordId", recordId),
          Filters.eq("goodsItem.eori", eori)
        )
      )
      .headOption()
  }

  def get(
    eori: String,
    lastUpdated: Option[Instant] = None,
    page: Int = 0,
    size: Int = 100000
  ): Future[GetGoodsItemRecordsResult] = Mdc.preservingMdc {
    collection
      .aggregate[GetGoodsItemRecordsResult](
        Seq(
          Aggregates.`match`(
            Filters.and(
              Filters.eq("goodsItem.eori", eori),
              lastUpdated.map(Filters.gt("metadata.updatedDateTime", _)).getOrElse(Filters.empty())
            )
          ),
          Aggregates.sort(Sorts.ascending("metadata.updatedDateTime")),
          Aggregates.facet(
            Facet("totalCount", Aggregates.count("count")),
            Facet("records", Aggregates.skip(page * size), Aggregates.limit(size))
          ),
          Aggregates.unwind("$totalCount"),
          Aggregates.project(
            Json
              .obj(
                "totalCount" -> "$totalCount.count",
                "records"    -> 1
              )
              .toDocument
          )
        )
      )
      .headOption()
      .map {
        _.getOrElse(GetGoodsItemRecordsResult(0, Seq.empty))
      }
  }

  def patchRecord(request: PatchGoodsItemRecordRequest): Future[Option[GoodsItemRecord]] = Mdc.preservingMdc {
    withSessionAndTransaction { session =>
      checkRecordState(session, request.recordId).flatMap { _ =>
        val updates = Seq(
          Some(Updates.set("goodsItem.actorId", request.actorId)),
          request.traderRef.map(Updates.set("goodsItem.traderRef", _)),
          request.comcode.map(Updates.set("goodsItem.comcode", _)),
          request.goodsDescription.map(Updates.set("goodsItem.goodsDescription", _)),
          request.countryOfOrigin.map(Updates.set("goodsItem.countryOfOrigin", _)),
          request.category.map(Updates.set("goodsItem.category", _)),
          request.assessments.map(Updates.set("goodsItem.assessments", _)),
          request.supplementaryUnit.map(Updates.set("goodsItem.supplementaryUnit", _)),
          request.measurementUnit.map(Updates.set("goodsItem.measurementUnit", _)),
          request.comcodeEffectiveFromDate.map(Updates.set("goodsItem.comcodeEffectiveFromDate", _)),
          request.comcodeEffectiveToDate.map(Updates.set("goodsItem.comcodeEffectiveToDate", _)),
          Some(Updates.set("metadata.updatedDateTime", clock.instant())),
          Some(Updates.inc("metadata.version", 1))
        ).flatten

        collection
          .findOneAndUpdate(
            session,
            Filters.and(
              Filters.eq("recordId", request.recordId),
              Filters.eq("goodsItem.eori", request.eori)
            ),
            Updates.combine(updates: _*),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
          )
          .headOption()
          .recoverWith {
            case e: MongoCommandException if isDuplicateKeyException(e) =>
              Future.failed(DuplicateEoriAndTraderRefException)
          }
      }
    }
  }

  def updateRecord(request: UpdateGoodsItemRecordRequest): Future[Option[GoodsItemRecord]] = Mdc.preservingMdc {
    withSessionAndTransaction { session =>
      checkRecordState(session, request.recordId).flatMap { _ =>
        val updates = Seq(
          Updates.set("goodsItem.actorId", request.actorId),
          Updates.set("goodsItem.traderRef", request.traderRef),
          Updates.set("goodsItem.comcode", request.comcode),
          Updates.set("goodsItem.goodsDescription", request.goodsDescription),
          Updates.set("goodsItem.countryOfOrigin", request.countryOfOrigin),
          request.category.fold(Updates.unset("goodsItem.category"))(Updates.set("goodsItem.category", _)),
          request.assessments.fold(Updates.unset("goodsItem.assessments"))(Updates.set("goodsItem.assessments", _)),
          request.supplementaryUnit.fold(Updates.unset("goodsItem.supplementaryUnit"))(
            Updates.set("goodsItem.supplementaryUnit", _)
          ),
          request.measurementUnit.fold(Updates.unset("goodsItem.measurementUnit"))(
            Updates.set("goodsItem.measurementUnit", _)
          ),
          Updates.set("goodsItem.comcodeEffectiveFromDate", request.comcodeEffectiveFromDate),
          request.comcodeEffectiveToDate.fold(Updates.unset("goodsItem.comcodeEffectiveToDate"))(
            Updates.set("goodsItem.comcodeEffectiveToDate", _)
          ),
          Updates.set("metadata.updatedDateTime", clock.instant()),
          Updates.inc("metadata.version", 1)
        )

        collection
          .findOneAndUpdate(
            session,
            Filters.and(
              Filters.eq("recordId", request.recordId),
              Filters.eq("goodsItem.eori", request.eori)
            ),
            Updates.combine(updates: _*),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
          )
          .headOption()
          .recoverWith {
            case e: MongoCommandException if isDuplicateKeyException(e) =>
              Future.failed(DuplicateEoriAndTraderRefException)
          }
      }
    }
  }

  def patch(request: PatchGoodsItemRequest): Future[Option[Done]] = Mdc.preservingMdc {

    val updates = Seq(
      request.accreditationStatus.map(x => Updates.set("metadata.accreditationStatus", x.entryName)),
      request.version.map(Updates.set("metadata.version", _)),
      request.active.map(Updates.set("metadata.active", _)),
      request.locked.map(Updates.set("metadata.locked", _)),
      request.toReview.map(Updates.set("metadata.toReview", _)),
      request.declarable.map(declarable => Updates.set("metadata.declarable", declarable.entryName)),
      request.reviewReason.map(Updates.set("metadata.reviewReason", _)),
      request.updatedDateTime.map(Updates.set("metadata.updatedDateTime", _))
    ).flatten

    if (updates.nonEmpty) {
      collection
        .findOneAndUpdate(
          Filters.and(
            Filters.eq("recordId", request.recordId),
            Filters.eq("goodsItem.eori", request.eori)
          ),
          Updates.combine(updates: _*),
          FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        )
        .toFutureOption()
        .map(_.map(_ => Done))
    } else {
      Future.successful(Some(Done))
    }
  }

  private def checkRecordState(session: ClientSession, recordId: String): Future[Done] =
    collection.find(session, Filters.eq("recordId", recordId)).headOption().flatMap {
      case Some(record) if record.metadata.locked  => Future.failed(RecordLockedException)
      case Some(record) if !record.metadata.active => Future.failed(RecordInactiveException)
      case _                                       => Future.successful(Done)
    }

  def deactivate(request: RemoveGoodsItemRecordRequest): Future[Option[GoodsItemRecord]] = Mdc.preservingMdc {

    collection
      .findOneAndUpdate(
        Filters.and(
          Filters.eq("recordId", request.recordId),
          Filters.eq("goodsItem.eori", request.eori)
        ),
        Updates.combine(
          Updates.set("metadata.active", false),
          Updates.inc("metadata.version", 1),
          Updates.set("goodsItem.actorId", request.actorId),
          Updates.set("metadata.updatedDateTime", clock.instant().truncatedTo(ChronoUnit.SECONDS))
        )
      )
      .headOption()
  }

  private def isDuplicateKeyException(e: MongoException): Boolean =
    e.getCode == 11000
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
        Indexes.ascending("goodsItem.traderRef", "goodsItem.eori"),
        IndexOptions()
          .name("traderRef_eori_idx")
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("metadata.updatedDateTime"),
        IndexOptions()
          .name("updatedDateTime_ttl_idx")
          .expireAfter(ttl.toSeconds, TimeUnit.SECONDS)
      )
    )
  }

  final case object DuplicateEoriAndTraderRefException extends Throwable with NoStackTrace
  final case object RecordLockedException extends Throwable with NoStackTrace
  final case object RecordInactiveException extends Throwable with NoStackTrace
}
