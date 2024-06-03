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

import cats.implicits.toFunctorOps
import org.apache.pekko.Done
import org.mongodb.scala.model._
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.TraderProfile
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.MaintainTraderProfileRequest

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TraderProfileRepository @Inject() (
                                            mongoComponent: MongoComponent,
                                            configuration: Configuration,
                                            clock: Clock
                                          )(implicit ec: ExecutionContext) extends PlayMongoRepository[TraderProfile](
  mongoComponent = mongoComponent,
  collectionName = "traderProfiles",
  domainFormat = TraderProfile.format,
  indexes = TraderProfileRepository.indexes(configuration)
) {

  def upsert(request: MaintainTraderProfileRequest): Future[Done] = {

    val profile = TraderProfile(
      eori = request.eori,
      actorId = request.actorId,
      ukimsNumber = request.ukimsNumber,
      nirmsNumber = request.nirmsNumber,
      niphlNumber = request.niphlNumber,
      lastUpdated = clock.instant()
    )

    collection.findOneAndReplace(
      Filters.eq("eori", request.eori),
      profile,
      FindOneAndReplaceOptions().upsert(true)
    ).toFuture().as(Done)
  }
}

object TraderProfileRepository {

  def indexes(configuration: Configuration): Seq[IndexModel] = {

    val ttl = configuration.get[FiniteDuration]("trader-profiles.ttl")

    Seq(
      IndexModel(
        Indexes.ascending("eori"),
        IndexOptions()
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("lastUpdated"),
        IndexOptions()
          .name("lastUpdated_ttl_idx")
          .expireAfter(ttl.toSeconds, TimeUnit.SECONDS)
      )
    )
  }
}