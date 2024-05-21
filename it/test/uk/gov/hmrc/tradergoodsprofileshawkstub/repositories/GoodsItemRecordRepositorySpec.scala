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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.slf4j.MDC
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.dispatchers.MDCPropagatingExecutorService
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateGoodsItemRecordRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
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
    with DefaultPlayMongoRepositorySupport[GoodsItemRecord] {

  private val clock: Clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)
  private val mockUuidService: UuidService = mock[UuidService]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "goods-item-records.ttl" -> "5 minutes"
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
      category = 1,
      assessments = Some(Seq(
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
      )),
      supplementaryUnit = Some(BigDecimal(2.5)),
      measurementUnit = Some("measurementUnit"),
      comcodeEffectiveFromDate = comcodeEffectiveFromDate,
      comcodeEffectiveToDate = Some(comcodeEffectiveToDate)
    )

    "must insert a new record with relevant details" in {

      when(mockUuidService.generate()).thenReturn("recordId")

      val expectedGoodsItemRecord = GoodsItemRecord(
        recordId = "recordId",
        goodsItem = GoodsItem(
          eori = "eori",
          actorId = "actorId",
          traderRef = "traderRef",
          comcode = "comcode",
          accreditationStatus = AccreditationStatus.NotRequested,
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
          comcodeEffectiveFromDate = comcodeEffectiveFromDate,
          comcodeEffectiveToDate = Some(comcodeEffectiveToDate)
        ),
        metadata = GoodsItemMetadata(
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
          createdDateTime = clock.instant(),
          updatedDateTime = clock.instant()
        )
      )

      val result = repository.insert(request).futureValue
      result mustEqual expectedGoodsItemRecord

      val results = repository.collection.find(Filters.eq("recordId", "recordId")).toFuture().futureValue
      results.length mustBe 1
      results.head mustBe result
    }

    mustPreserveMdc {
      when(mockUuidService.generate()).thenReturn("recordId")
      repository.insert(request)
    }
  }

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
