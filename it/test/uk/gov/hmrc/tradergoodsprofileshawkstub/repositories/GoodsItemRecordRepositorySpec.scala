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

import org.mockito.Mockito
import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalactic.source.Position
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.slf4j.MDC
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.dispatchers.MDCPropagatingExecutorService
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests._
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository.{DuplicateEoriAndTraderRefException, RecordInactiveException, RecordLockedException}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

class GoodsItemRecordRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues
    with DefaultPlayMongoRepositorySupport[GoodsItemRecord] {

  private val clock: Clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)
  private val mockUuidService: UuidService = mock[UuidService]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "goods-item-records.ttl" -> "1 day"
    )
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
      bind[Clock].toInstance(clock),
      bind[UuidService].toInstance(mockUuidService)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUuidService)
  }

  override protected lazy val repository: GoodsItemRecordRepository =
    app.injector.instanceOf[GoodsItemRecordRepository]

  "insert" - {

    val comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS)
    val comcodeEffectiveToDate = clock.instant().plus(1, ChronoUnit.DAYS)

    val request = CreateGoodsItemRecordRequest(
      eori = "eori",
      actorId = "actorId",
      traderRef = "traderRef",
      comcode = "comcode",
      goodsDescription = "goodsDescription",
      countryOfOrigin = "countryOfOrigin",
      category = Some(Category.Controlled),
      assessments = Some(Seq(
        Assessment(
          assessmentId = Some("assessmentId"),
          primaryCategory = Some(Category.Controlled),
          condition = Some(Condition(
            `type` = Some("type"),
            conditionId = Some("conditionId"),
            conditionDescription = Some("conditionDescription"),
            conditionTraderText = Some("conditionTraderText")
          ))
        )
      )),
      supplementaryUnit = Some(BigDecimal(2.5)),
      measurementUnit = Some("measurementUnit"),
      comcodeEffectiveFromDate = comcodeEffectiveFromDate,
      comcodeEffectiveToDate = Some(comcodeEffectiveToDate)
    )

    "must insert a new record with relevant details" in {

      when(mockUuidService.generate()).thenReturn("recordId")

      val expectedGoodsItemRecord = generateRecord.copy(
        recordId = "recordId",
        goodsItem = generateRecord.goodsItem.copy(
          traderRef = "traderRef"
        ),
        metadata = generateRecord.metadata.copy(
          updatedDateTime = clock.instant(),
          createdDateTime = clock.instant()
        )
      )

      val result = repository.insert(request).futureValue
      result mustEqual expectedGoodsItemRecord

      val results = repository.collection.find(Filters.eq("recordId", "recordId")).toFuture().futureValue
      results.length mustBe 1
      results.head mustBe result
    }

    "must fail if a record already exists with that eori/traderRef" in {

      when(mockUuidService.generate())
        .thenReturn("recordId", "recordId2")

      val result = repository.insert(request).futureValue
      val error = repository.insert(request).failed.futureValue

      error mustBe DuplicateEoriAndTraderRefException

      val results = repository.collection.find(Filters.eq("recordId", "recordId")).toFuture().futureValue
      results.length mustBe 1
      results.head mustBe result
    }

    mustPreserveMdc {
      when(mockUuidService.generate()).thenReturn("recordId")
      repository.insert(request)
    }
  }

  "getById" - {

    "must return a record with the matching recordId and eori when it exists" in {

      val record1 = generateRecord
      val record2 = generateRecord
      val record3 = generateRecord

      repository.collection.insertMany(Seq(record1, record2, record3)).toFuture().futureValue

      val result = repository.getById(record2.goodsItem.eori, record2.recordId).futureValue.value

      result mustBe record2
    }

    "must return none when the recordId does not match" in {

      val record = generateRecord

      repository.collection.insertOne(record).toFuture().futureValue
      repository.getById(record.goodsItem.eori, "recordId").futureValue mustBe None
    }

    "must return none when eori does not match" in {

      val record = generateRecord

      repository.collection.insertOne(record).toFuture().futureValue
      repository.getById("foobar", record.recordId).futureValue mustBe None
    }

    mustPreserveMdc(repository.getById("eori", "recordId"))
  }

  "get" - {

    val recordsToMatch = for (i <- 0 until 10) yield {
      val record = generateRecord
      record.copy(
        goodsItem = record.goodsItem.copy(eori = "eori"),
        metadata = record.metadata.copy(
          createdDateTime = record.metadata.createdDateTime.minus(i, ChronoUnit.DAYS),
          updatedDateTime = record.metadata.updatedDateTime.minus(i, ChronoUnit.DAYS)
        )
      )
    }

    val recordsToIgnore = recordsToMatch.map(r => r.copy(
      recordId = UUID.randomUUID().toString,
      goodsItem = r.goodsItem.copy(eori = UUID.randomUUID().toString)
    ))

    "must return the relevant records when there is no lastUpdated" in {

      repository.collection.insertMany(recordsToMatch ++ recordsToIgnore).toFuture().futureValue

      val result = repository.get("eori", page = 1, size = 3).futureValue

      result.totalCount mustBe 10
      result.records.length mustBe 3
      result.records mustBe recordsToMatch.reverse.slice(3, 6).toList
    }

    "must return the relevant records when there is a lastUpdated" in {

      repository.collection.insertMany(recordsToMatch ++ recordsToIgnore).toFuture().futureValue

      val result = repository.get("eori", lastUpdated = Some(clock.instant().minus(8, ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS)), page = 1, size = 3).futureValue

      result.totalCount mustBe 9
      result.records.length mustBe 3
      result.records mustBe recordsToMatch.reverse.slice(4, 7).toList
    }

    "must return totalCount of 0 when there are no records" in {

      val result = repository.get("eori", lastUpdated = Some(clock.instant().minus(8, ChronoUnit.DAYS)), page = 1, size = 3).futureValue

      result.totalCount mustBe 0
      result.records.length mustBe 0
    }

    mustPreserveMdc(repository.get("eori"))
  }

  "patch record" - {

    "when the recordId and eori matches" - {

      "must set properties when they are provided" in {

        val record = generateRecord.copy(
          metadata = generateRecord.metadata.copy(
            updatedDateTime = clock.instant().minus(1, ChronoUnit.HOURS),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val request = PatchGoodsItemRecordRequest(
          recordId = record.recordId,
          eori = record.goodsItem.eori,
          actorId = "anotherActorId",
          traderRef = Some("anotherTraderRef"),
          comcode = Some("anotherComcode"),
          goodsDescription = Some("anotherGoodsDescription"),
          countryOfOrigin = Some("anotherCountryOfOrigin"),
          category = Some(Category.Excluded),
          assessments = Some(Seq(
            Assessment(
              assessmentId = Some("anotherAssessmentId"),
              primaryCategory = Some(Category.Standard),
              condition = Some(
                Condition(
                  `type` = Some("anotherType"),
                  conditionId = Some("anotherConditionId"),
                  conditionDescription = Some("anotherConditionDescription"),
                  conditionTraderText = Some("anotherConditionTraderText")
                )
              )
            )
          )),
          supplementaryUnit = Some(BigDecimal(3.5)),
          measurementUnit = Some("anotherMeasurementUnit"),
          comcodeEffectiveFromDate = Some(record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS)),
          comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
        )

        val expectedResult = GoodsItemRecord(
          recordId = record.recordId,
          goodsItem = GoodsItem(
            eori = "eori",
            actorId = "anotherActorId",
            traderRef = "anotherTraderRef",
            comcode = "anotherComcode",
            goodsDescription = "anotherGoodsDescription",
            countryOfOrigin = "anotherCountryOfOrigin",
            category = Some(Category.Excluded),
            assessments = Some(Seq(
              Assessment(
                assessmentId = Some("anotherAssessmentId"),
                primaryCategory = Some(Category.Standard),
                condition = Some(Condition(
                  `type` = Some("anotherType"),
                  conditionId = Some("anotherConditionId"),
                  conditionDescription = Some("anotherConditionDescription"),
                  conditionTraderText = Some("anotherConditionTraderText")
                ))
              )
            )),
            supplementaryUnit = Some(BigDecimal(3.5)),
            measurementUnit = Some("anotherMeasurementUnit"),
            comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.SECONDS),
            comcodeEffectiveToDate = Some(clock.instant().plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.SECONDS))
          ),
          metadata = GoodsItemMetadata(
            accreditationStatus = AccreditationStatus.NotRequested,
            version = 2,
            active = true,
            locked = false,
            toReview = false,
            declarable = None,
            reviewReason = None,
            srcSystemName = "MDTP",
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS),
            updatedDateTime = clock.instant()
          )
        )

        repository.collection.insertOne(record).toFuture().futureValue

        val result = repository.patchRecord(request).futureValue.value

        result mustEqual expectedResult
        repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual expectedResult
      }

      "must not update properties that aren't provided" in {

        val record = generateRecord.copy(
          metadata = generateRecord.metadata.copy(
            updatedDateTime = clock.instant().minus(1, ChronoUnit.HOURS),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val request = PatchGoodsItemRecordRequest(
          recordId = record.recordId,
          eori = record.goodsItem.eori,
          actorId = "anotherActorId",
          traderRef = None,
          comcode = None,
          goodsDescription = None,
          countryOfOrigin = None,
          category = None,
          assessments = None,
          supplementaryUnit = None,
          measurementUnit = None,
          comcodeEffectiveFromDate = None,
          comcodeEffectiveToDate = None
        )

        val expectedResult = GoodsItemRecord(
          recordId = record.recordId,
          goodsItem = GoodsItem(
            eori = "eori",
            actorId = "anotherActorId",
            traderRef = record.goodsItem.traderRef,
            comcode = "comcode",
            goodsDescription = "goodsDescription",
            countryOfOrigin = "countryOfOrigin",
            category = Some(Category.Controlled),
            assessments = Some(Seq(
              Assessment(
                assessmentId = Some("assessmentId"),
                primaryCategory = Some(Category.Controlled),
                condition = Some(Condition(
                  `type` = Some("type"),
                  conditionId = Some("conditionId"),
                  conditionDescription = Some("conditionDescription"),
                  conditionTraderText = Some("conditionTraderText")
                ))
              )
            )),
            supplementaryUnit = Some(BigDecimal(2.5)),
            measurementUnit = Some("measurementUnit"),
            comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
            comcodeEffectiveToDate = Some(clock.instant().plus(1, ChronoUnit.DAYS))
          ),
          metadata = GoodsItemMetadata(
            accreditationStatus = AccreditationStatus.NotRequested,
            version = 2,
            active = true,
            locked = false,
            toReview = false,
            declarable = None,
            reviewReason = None,
            srcSystemName = "MDTP",
            updatedDateTime = clock.instant(),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        repository.collection.insertOne(record).toFuture().futureValue

        val result = repository.patchRecord(request).futureValue.value

        result mustEqual expectedResult
        repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual expectedResult
      }

      "must fail when the new traderRef already exists in the database" in {

        val record = generateRecord

        val request = PatchGoodsItemRecordRequest(
          recordId = record.recordId,
          eori = record.goodsItem.eori,
          actorId = "anotherActorId",
          traderRef = Some("traderRef2"),
          comcode = Some("anotherComcode"),
          goodsDescription = Some("anotherGoodsDescription"),
          countryOfOrigin = Some("anotherCountryOfOrigin"),
          category = Some(Category.Excluded),
          assessments = Some(Seq(
            Assessment(
              assessmentId = Some("anotherAssessmentId"),
              primaryCategory = Some(Category.Standard),
              condition = Some(
                Condition(
                  `type` = Some("anotherType"),
                  conditionId = Some("anotherConditionId"),
                  conditionDescription = Some("anotherConditionDescription"),
                  conditionTraderText = Some("anotherConditionTraderText")
                )
              )
            )
          )),
          supplementaryUnit = Some(BigDecimal(3.5)),
          measurementUnit = Some("anotherMeasurementUnit"),
          comcodeEffectiveFromDate = Some(record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS)),
          comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
        )

        repository.collection.insertOne(record).toFuture().futureValue
        repository.collection.insertOne(record.copy(recordId = "recordId2", goodsItem = record.goodsItem.copy(traderRef = "traderRef2"))).toFuture().futureValue

        val result = repository.patchRecord(request).failed.futureValue
        result mustBe DuplicateEoriAndTraderRefException

        repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
      }

      "must fail when the record is locked" in {

        val record = generateRecord.copy(metadata = generateRecord.metadata.copy(locked = true))

        val request = PatchGoodsItemRecordRequest(
          recordId = record.recordId,
          eori = record.goodsItem.eori,
          actorId = "anotherActorId",
          traderRef = Some("anotherTraderRef"),
          comcode = Some("anotherComcode"),
          goodsDescription = Some("anotherGoodsDescription"),
          countryOfOrigin = Some("anotherCountryOfOrigin"),
          category = Some(Category.Excluded),
          assessments = Some(Seq(
            Assessment(
              assessmentId = Some("anotherAssessmentId"),
              primaryCategory = Some(Category.Standard),
              condition = Some(
                Condition(
                  `type` = Some("anotherType"),
                  conditionId = Some("anotherConditionId"),
                  conditionDescription = Some("anotherConditionDescription"),
                  conditionTraderText = Some("anotherConditionTraderText")
                )
              )
            )
          )),
          supplementaryUnit = Some(BigDecimal(3.5)),
          measurementUnit = Some("anotherMeasurementUnit"),
          comcodeEffectiveFromDate = Some(record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS)),
          comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
        )

        repository.collection.insertOne(record).toFuture().futureValue

        val result = repository.patchRecord(request).failed.futureValue
        result mustBe RecordLockedException

        repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
      }

      "must fail when the record is inactive" in {

        val record = generateRecord.copy(metadata = generateRecord.metadata.copy(active = false))

        val request = PatchGoodsItemRecordRequest(
          recordId = record.recordId,
          eori = record.goodsItem.eori,
          actorId = "anotherActorId",
          traderRef = Some("anotherTraderRef"),
          comcode = Some("anotherComcode"),
          goodsDescription = Some("anotherGoodsDescription"),
          countryOfOrigin = Some("anotherCountryOfOrigin"),
          category = Some(Category.Excluded),
          assessments = Some(Seq(
            Assessment(
              assessmentId = Some("anotherAssessmentId"),
              primaryCategory = Some(Category.Standard),
              condition = Some(
                Condition(
                  `type` = Some("anotherType"),
                  conditionId = Some("anotherConditionId"),
                  conditionDescription = Some("anotherConditionDescription"),
                  conditionTraderText = Some("anotherConditionTraderText")
                )
              )
            )
          )),
          supplementaryUnit = Some(BigDecimal(3.5)),
          measurementUnit = Some("anotherMeasurementUnit"),
          comcodeEffectiveFromDate = Some(record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS)),
          comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
        )

        repository.collection.insertOne(record).toFuture().futureValue

        val result = repository.patchRecord(request).failed.futureValue
        result mustBe RecordInactiveException

        repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
      }
    }

    "must not update when the recordId doesn't match" in {

      val record = generateRecord

      val request = PatchGoodsItemRecordRequest(
        recordId = s"another${record.recordId}",
        eori = record.goodsItem.eori,
        actorId = "anotherActorId",
        traderRef = Some("anotherTraderRef"),
        comcode = Some("anotherComcode"),
        goodsDescription = Some("anotherGoodsDescription"),
        countryOfOrigin = Some("anotherCountryOfOrigin"),
        category = Some(Category.Excluded),
        assessments = Some(Seq(
          Assessment(
            assessmentId = Some("anotherAssessmentId"),
            primaryCategory = Some(Category.Standard),
            condition = Some(
              Condition(
                `type` = Some("anotherType"),
                conditionId = Some("anotherConditionId"),
                conditionDescription = Some("anotherConditionDescription"),
                conditionTraderText = Some("anotherConditionTraderText")
              )
            )
          )
        )),
        supplementaryUnit = Some(BigDecimal(3.5)),
        measurementUnit = Some("anotherMeasurementUnit"),
        comcodeEffectiveFromDate = Some(record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS)),
        comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
      )

      repository.collection.insertOne(record).toFuture().futureValue

      repository.patchRecord(request).futureValue mustBe None
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
    }

    "must not update when the eori doesn't match" in {

      val record = generateRecord

      val request = PatchGoodsItemRecordRequest(
        recordId = record.recordId,
        eori = s"another${record.goodsItem.eori}",
        actorId = "anotherActorId",
        traderRef = Some("anotherTraderRef"),
        comcode = Some("anotherComcode"),
        goodsDescription = Some("anotherGoodsDescription"),
        countryOfOrigin = Some("anotherCountryOfOrigin"),
        category = Some(Category.Excluded),
        assessments = Some(Seq(
          Assessment(
            assessmentId = Some("anotherAssessmentId"),
            primaryCategory = Some(Category.Standard),
            condition = Some(
              Condition(
                `type` = Some("anotherType"),
                conditionId = Some("anotherConditionId"),
                conditionDescription = Some("anotherConditionDescription"),
                conditionTraderText = Some("anotherConditionTraderText")
              )
            )
          )
        )),
        supplementaryUnit = Some(BigDecimal(3.5)),
        measurementUnit = Some("anotherMeasurementUnit"),
        comcodeEffectiveFromDate = Some(record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS)),
        comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
      )

      repository.collection.insertOne(record).toFuture().futureValue

      repository.patchRecord(request).futureValue mustBe None
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
    }
  }

  "update whole record" - {
    def buildRequest(record: GoodsItemRecord) = {
      UpdateGoodsItemRecordRequest(
        recordId = record.recordId,
        eori = record.goodsItem.eori,
        actorId = "anotherActorId",
        traderRef = "anotherTraderRef",
        comcode = "anotherComcode",
        goodsDescription = "anotherGoodsDescription",
        countryOfOrigin = "anotherCountryOfOrigin",
        category = Some(Category.Excluded),
        assessments = Some(Seq(
          Assessment(
            assessmentId = Some("anotherAssessmentId"),
            primaryCategory = Some(Category.Standard),
            condition = Some(
              Condition(
                `type` = Some("anotherType"),
                conditionId = Some("anotherConditionId"),
                conditionDescription = Some("anotherConditionDescription"),
                conditionTraderText = Some("anotherConditionTraderText")
              )
            )
          )
        )),
        supplementaryUnit = Some(BigDecimal(3.5)),
        measurementUnit = Some("anotherMeasurementUnit"),
        comcodeEffectiveFromDate = record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS),
        comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
      )
    }

     "should replace the entire record with the given request" in {
       val record = generateRecord.copy(
         metadata = generateRecord.metadata.copy(
           updatedDateTime = clock.instant().minus(1, ChronoUnit.HOURS),
           createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
         )
       )

       val request = buildRequest(record)

       val expectedResult = GoodsItemRecord(
         recordId = record.recordId,
         goodsItem = GoodsItem(
           eori = "eori",
           actorId = "anotherActorId",
           traderRef = "anotherTraderRef",
           comcode = "anotherComcode",
           goodsDescription = "anotherGoodsDescription",
           countryOfOrigin = "anotherCountryOfOrigin",
           category = Some(Category.Excluded),
           assessments = Some(Seq(
             Assessment(
               assessmentId = Some("anotherAssessmentId"),
               primaryCategory = Some(Category.Standard),
               condition = Some(Condition(
                 `type` = Some("anotherType"),
                 conditionId = Some("anotherConditionId"),
                 conditionDescription = Some("anotherConditionDescription"),
                 conditionTraderText = Some("anotherConditionTraderText")
               ))
             )
           )),
           supplementaryUnit = Some(BigDecimal(3.5)),
           measurementUnit = Some("anotherMeasurementUnit"),
           comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.SECONDS),
           comcodeEffectiveToDate = Some(clock.instant().plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.SECONDS))
         ),
         metadata = GoodsItemMetadata(
           accreditationStatus = AccreditationStatus.NotRequested,
           version = 2,
           active = true,
           locked = false,
           toReview = false,
           declarable = None,
           reviewReason = None,
           srcSystemName = "MDTP",
           createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS),
           updatedDateTime = clock.instant()
         )
       )

       insert(record).futureValue

       val result = repository.updateRecord(request).futureValue.value
       val value = find(Filters.eq("recordId", record.recordId)).futureValue

       result mustEqual expectedResult
       value.head mustEqual expectedResult
     }

    "must fail when the record is locked" in {
      val record = generateRecord.copy(metadata = generateRecord.metadata.copy(locked = true))

      val request = buildRequest(record)

      repository.collection.insertOne(record).toFuture().futureValue

      val result = repository.updateRecord(request).failed.futureValue
      result mustBe RecordLockedException

      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
    }

    "must not update when the recordId doesn't match" in {
      val record = generateRecord

      val request = UpdateGoodsItemRecordRequest(
        recordId = s"some-other${record.recordId}",
        eori = record.goodsItem.eori,
        actorId = "anotherActorId",
        traderRef = "anotherTraderRef",
        comcode = "anotherComcode",
        goodsDescription = "anotherGoodsDescription",
        countryOfOrigin = "anotherCountryOfOrigin",
        category = Some(Category.Excluded),
        assessments = Some(Seq(
          Assessment(
            assessmentId = Some("anotherAssessmentId"),
            primaryCategory = Some(Category.Standard),
            condition = Some(
              Condition(
                `type` = Some("anotherType"),
                conditionId = Some("anotherConditionId"),
                conditionDescription = Some("anotherConditionDescription"),
                conditionTraderText = Some("anotherConditionTraderText")
              )
            )
          )
        )),
        supplementaryUnit = Some(BigDecimal(3.5)),
        measurementUnit = Some("anotherMeasurementUnit"),
        comcodeEffectiveFromDate = record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS),
        comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
      )

      repository.collection.insertOne(record).toFuture().futureValue

      repository.updateRecord(request).futureValue mustBe None
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
    }

    "must not update when the eori doesn't match" in {
      val record = generateRecord

      val request = UpdateGoodsItemRecordRequest(
        recordId = record.recordId,
        eori = s"some-other${record.goodsItem.eori}",
        actorId = "anotherActorId",
        traderRef = "anotherTraderRef",
        comcode = "anotherComcode",
        goodsDescription = "anotherGoodsDescription",
        countryOfOrigin = "anotherCountryOfOrigin",
        category = Some(Category.Excluded),
        assessments = Some(Seq(
          Assessment(
            assessmentId = Some("anotherAssessmentId"),
            primaryCategory = Some(Category.Standard),
            condition = Some(
              Condition(
                `type` = Some("anotherType"),
                conditionId = Some("anotherConditionId"),
                conditionDescription = Some("anotherConditionDescription"),
                conditionTraderText = Some("anotherConditionTraderText")
              )
            )
          )
        )),
        supplementaryUnit = Some(BigDecimal(3.5)),
        measurementUnit = Some("anotherMeasurementUnit"),
        comcodeEffectiveFromDate = record.goodsItem.comcodeEffectiveFromDate.plus(30, ChronoUnit.SECONDS),
        comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate.map(_.plus(30, ChronoUnit.SECONDS))
      )

      repository.collection.insertOne(record).toFuture().futureValue

      repository.updateRecord(request).futureValue mustBe None
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
    }
  }

  "patch" - {

    "must set properties when they are provided" in {

      val record = generateRecord

      val request = PatchGoodsItemRequest(
        eori = record.goodsItem.eori,
        recordId = record.recordId,
        accreditationStatus = None,
        version = Some(123),
        active = None,
        locked = None,
        toReview = None,
        declarable = None,
        reviewReason = None,
        updatedDateTime = None
      )

      val expectedResult = record.copy(
        metadata = GoodsItemMetadata(
          accreditationStatus = record.metadata.accreditationStatus,
          version = 123,
          active = record.metadata.active,
          locked = record.metadata.locked,
          toReview = record.metadata.toReview,
          declarable = record.metadata.declarable,
          reviewReason = record.metadata.reviewReason,
          srcSystemName = record.metadata.srcSystemName,
          createdDateTime = record.metadata.createdDateTime,
          updatedDateTime = record.metadata.updatedDateTime
        )
      )

      repository.collection.insertOne(record).toFuture().futureValue

      val result = repository.patch(request).futureValue

      result mustBe defined
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual expectedResult
    }

    "must not set properties that are not provided" in {

      val record = generateRecord

      val request = PatchGoodsItemRequest(
        eori = record.goodsItem.eori,
        recordId = record.recordId,
        accreditationStatus = None,
        version = None,
        active = None,
        locked = None,
        toReview = None,
        declarable = None,
        reviewReason = None,
        updatedDateTime = None
      )

      val expectedResult = record.copy(
        metadata = GoodsItemMetadata(
          accreditationStatus = record.metadata.accreditationStatus,
          version = record.metadata.version,
          active = record.metadata.active,
          locked = record.metadata.locked,
          toReview = record.metadata.toReview,
          declarable = record.metadata.declarable,
          reviewReason = record.metadata.reviewReason,
          srcSystemName = record.metadata.srcSystemName,
          createdDateTime = record.metadata.createdDateTime,
          updatedDateTime = record.metadata.updatedDateTime
        )
      )

      repository.collection.insertOne(record).toFuture().futureValue

      val result = repository.patch(request).futureValue

      result mustBe defined
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual expectedResult
    }

    "must succeed when given no fields to change" in {

      val record = generateRecord

      val request = PatchGoodsItemRequest(
        eori = record.goodsItem.eori,
        recordId = record.recordId,
        accreditationStatus = None,
        version = None,
        active = None,
        locked = None,
        toReview = None,
        declarable = None,
        reviewReason = None,
        updatedDateTime = None
      )

      repository.collection.insertOne(record).toFuture().futureValue

      val result = repository.patch(request).futureValue

      result mustBe defined
      repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue mustEqual record
    }

    "must return None when asked to update a record that does not exist" in {

      val record = generateRecord

      val request = PatchGoodsItemRequest(
        eori = record.goodsItem.eori,
        recordId = "some other record id",
        accreditationStatus = None,
        version = Some(123),
        active = None,
        locked = None,
        toReview = None,
        declarable = None,
        reviewReason = None,
        updatedDateTime = None
      )

      repository.collection.insertOne(record).toFuture().futureValue

      val result = repository.patch(request).futureValue

      result must not be defined
    }
  }

  "deactivate" - {

    "must set the `active` property to false, increment the version, set the actorId, set the updatedDateTime, and return the old state of the record when the existing record is active" in {

      val record = generateRecord
      val expectedRecord = record.copy(
        metadata = record.metadata.copy(
          active = false,
          version = 2,
          updatedDateTime = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        ),
        goodsItem = record.goodsItem.copy(actorId = "newActorId")
      )

      repository.collection.insertOne(record).toFuture().futureValue

      val request = RemoveGoodsItemRecordRequest(record.goodsItem.eori, record.recordId, "newActorId")
      val result = repository.deactivate(request).futureValue.value

      result mustEqual record

      val updatedRecord = repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue
      updatedRecord mustEqual expectedRecord
    }

    "must set the `active` property to false, increment the version, set the actorId, set the updatedDateTime and return the old state of the record when the existing record is not active" in {

      val record = generateRecord.copy(metadata = generateRecord.metadata.copy(active = false))
      val expectedRecord = record.copy(
        metadata = record.metadata.copy(
          version = 2,
          updatedDateTime = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        ),
        goodsItem = record.goodsItem.copy(actorId = "newActorId")
      )

      repository.collection.insertOne(record).toFuture().futureValue

      val request = RemoveGoodsItemRecordRequest(record.goodsItem.eori, record.recordId, "newActorId")
      val result = repository.deactivate(request).futureValue.value

      result mustEqual record

      val updatedRecord = repository.collection.find(Filters.eq("recordId", record.recordId)).head().futureValue
      updatedRecord mustEqual expectedRecord
    }

    "must return none when there is no existing record" in {

      val request = RemoveGoodsItemRecordRequest("eori", UUID.randomUUID().toString, "actorId")
      val result = repository.deactivate(request).futureValue
      result mustBe None
    }
  }

  private def generateRecord = GoodsItemRecord(
    recordId = UUID.randomUUID().toString,
    goodsItem = GoodsItem(
      eori = "eori",
      actorId = "actorId",
      traderRef = UUID.randomUUID().toString,
      comcode = "comcode",
      goodsDescription = "goodsDescription",
      countryOfOrigin = "countryOfOrigin",
      category = Some(Category.Controlled),
      assessments = Some(Seq(
        Assessment(
          assessmentId = Some("assessmentId"),
          primaryCategory = Some(Category.Controlled),
          condition = Some(Condition(
            `type` = Some("type"),
            conditionId = Some("conditionId"),
            conditionDescription = Some("conditionDescription"),
            conditionTraderText = Some("conditionTraderText")
          ))
        )
      )),
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
      toReview = false,
      declarable = None,
      reviewReason = None,
      srcSystemName = "MDTP",
      updatedDateTime = clock.instant(),
      createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
    )
  )

  private def mustPreserveMdc[A](f: => Future[A])(implicit pos: Position): Unit =
    "must preserve MDC" in {

      implicit lazy val ec: ExecutionContext =
        ExecutionContext.fromExecutor(new MDCPropagatingExecutorService(Executors.newFixedThreadPool(2)))

      MDC.put("test", "foo")

      f.map { _ =>
        MDC.get("test") mustEqual "foo"
      }.futureValue
    }
}
