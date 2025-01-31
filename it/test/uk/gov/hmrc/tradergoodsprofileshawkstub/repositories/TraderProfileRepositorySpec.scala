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

import org.mongodb.scala.gridfs.ObservableFuture
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.TraderProfile
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.{CreateTraderProfileRequest, MaintainTraderProfileRequest}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository.DuplicateEoriException

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}

class TraderProfileRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues
    with DefaultPlayMongoRepositorySupport[TraderProfile] {

  private val clock: Clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "trader-profiles.ttl" -> "1 day"
    )
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
      bind[Clock].toInstance(clock)
    )
    .build()

  override protected val repository: TraderProfileRepository = app.injector.instanceOf[TraderProfileRepository]

  "upsert" - {

    val request = MaintainTraderProfileRequest(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims"), nirmsNumber = Some("nirms"), niphlNumber = Some("niphl"))

    "must add a trader profile if it doesn't exist" in {

      val expectedProfile = TraderProfile(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims"), nirmsNumber = Some("nirms"), niphlNumber = Some("niphl"), lastUpdated = clock.instant())

      repository.upsert(request).futureValue

      val profiles = repository.collection.find().toFuture().futureValue
      profiles.size mustBe 1
      profiles.head mustEqual expectedProfile
    }

    "must update a trader profile if it exists" in {

      val expectedProfile = TraderProfile(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims2"), nirmsNumber = Some("nirms2"), niphlNumber = Some("niphl2"), lastUpdated = clock.instant())
      val updatedRequest = request.copy(ukimsNumber = Some("ukims2"), nirmsNumber = Some("nirms2"), niphlNumber = Some("niphl2"))

      repository.upsert(request).futureValue
      repository.upsert(updatedRequest).futureValue

      val profiles = repository.collection.find().toFuture().futureValue
      profiles.size mustBe 1
      profiles.head mustEqual expectedProfile
    }
  }

  "get" - {

    val profile = TraderProfile(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims"), nirmsNumber = Some("nirms"), niphlNumber = Some("niphl"), lastUpdated = clock.instant())

    "must return the profile for a given eori when it exists" in {
      repository.collection.insertOne(profile).toFuture().futureValue
      repository.get("eori").futureValue.value mustBe profile
    }

    "must return None when there is no profile for the given eori" in {
      repository.get("eori").futureValue mustBe None
    }
  }

  "insert" - {

    val request = CreateTraderProfileRequest(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims"), nirmsNumber = Some("nirms"), niphlNumber = Some("niphl"))

    "must add a new trader profile" in {

      val expectedProfile = TraderProfile(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims"), nirmsNumber = Some("nirms"), niphlNumber = Some("niphl"), lastUpdated = clock.instant())

      repository.insert(request).futureValue

      val profiles = repository.collection.find().toFuture().futureValue
      profiles.size mustBe 1
      profiles.head mustEqual expectedProfile
    }

    "must fail if a record already exists with that eori" in {

      val expectedProfile = TraderProfile(eori = "eori", actorId = "actorId", ukimsNumber = Some("ukims"), nirmsNumber = Some("nirms"), niphlNumber = Some("niphl"), lastUpdated = clock.instant())

      repository.insert(request).futureValue
      val error = repository.insert(request).failed.futureValue

      error mustBe DuplicateEoriException
      repository.get("eori").futureValue.value mustBe expectedProfile
    }
  }
}
