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

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
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
            category = Category.Controlled,
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
            toReview = true,
            declarable = Some("IMMI ready"),
            reviewReason = Some("reviewReason"),
            // ukimsNumber = Some("ukims"),
            // nirmsNumber = Some("nirms"),
            // niphlNumber = Some("niphl"),
            srcSystemName = "MDTP",
            updatedDateTime = clock.instant(),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val profile = TraderProfile(
          eori = "eori",
          actorId = "actorId",
          ukimsNumber = Some("ukims"),
          nirmsNumber = Some("nirms"),
          niphlNumber = Some("niphl"),
          lastUpdated = clock.instant()
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
          "declarable" -> fullGoodsItemRecord.declarable(clock.instant()),
          "ukimsNumber" -> profile.ukimsNumber,
          "nirmsNumber" -> profile.nirmsNumber,
          "niphlNumber" -> profile.niphlNumber,
          "createdDateTime" -> fullGoodsItemRecord.metadata.createdDateTime,
          "updatedDateTime" -> fullGoodsItemRecord.metadata.updatedDateTime
        )

        fullGoodsItemRecord.toCreateRecordResponse(profile, clock.instant()) mustEqual expectedJson
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
            category = Category.Controlled,
            assessments = Some(Seq(
              Assessment(
                assessmentId = None,
                primaryCategory = None,
                condition = None
              )
            )),
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
            declarable = None,
            reviewReason = None,
            srcSystemName = "MDTP",
            updatedDateTime = clock.instant(),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val profile = TraderProfile(
          eori = "eori",
          actorId = "actorId",
          ukimsNumber = None,
          nirmsNumber = None,
          niphlNumber = None,
          lastUpdated = clock.instant()
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
          "declarable" -> minimumGoodsItemRecord.declarable(clock.instant()),
          "createdDateTime" -> minimumGoodsItemRecord.metadata.createdDateTime,
          "updatedDateTime" -> minimumGoodsItemRecord.metadata.updatedDateTime
        )

        minimumGoodsItemRecord.toCreateRecordResponse(profile, clock.instant()) mustEqual expectedJson
      }
    }
  }

  "toGetRecordResponse" - {

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
            category = Category.Controlled,
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
            toReview = true,
            declarable = Some("IMMI ready"),
            reviewReason = Some("reviewReason"),
            srcSystemName = "MDTP",
            updatedDateTime = clock.instant(),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val profile = TraderProfile(
          eori = "eori",
          actorId = "actorId",
          ukimsNumber = Some("ukims"),
          nirmsNumber = Some("nirms"),
          niphlNumber = Some("niphl"),
          lastUpdated = clock.instant()
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
          "declarable" -> fullGoodsItemRecord.metadata.declarable,
          "reviewReason" -> fullGoodsItemRecord.metadata.reviewReason,
          "locked" -> fullGoodsItemRecord.metadata.locked,
          "srcSystemName" -> fullGoodsItemRecord.metadata.srcSystemName,
          "ukimsNumber" -> profile.ukimsNumber,
          "nirmsNumber" -> profile.nirmsNumber,
          "niphlNumber" -> profile.niphlNumber,
          "createdDateTime" -> fullGoodsItemRecord.metadata.createdDateTime,
          "updatedDateTime" -> fullGoodsItemRecord.metadata.updatedDateTime
        )

        fullGoodsItemRecord.toGetRecordResponse(profile, clock.instant()) mustEqual expectedJson
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
            category = Category.Controlled,
            assessments = None,
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
            declarable = None,
            reviewReason = None,
            srcSystemName = "MDTP",
            updatedDateTime = clock.instant(),
            createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
          )
        )

        val profile = TraderProfile(
          eori = "eori",
          actorId = "actorId",
          ukimsNumber = None,
          nirmsNumber = None,
          niphlNumber = None,
          lastUpdated = clock.instant()
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
          "comcodeEffectiveFromDate" -> minimumGoodsItemRecord.goodsItem.comcodeEffectiveFromDate,
          "version" -> minimumGoodsItemRecord.metadata.version,
          "active" -> minimumGoodsItemRecord.metadata.active,
          "toReview" -> minimumGoodsItemRecord.metadata.toReview,
          "locked" -> minimumGoodsItemRecord.metadata.locked,
          "srcSystemName" -> minimumGoodsItemRecord.metadata.srcSystemName,
          "createdDateTime" -> minimumGoodsItemRecord.metadata.createdDateTime,
          "updatedDateTime" -> minimumGoodsItemRecord.metadata.updatedDateTime
        )

        minimumGoodsItemRecord.toGetRecordResponse(profile, clock.instant()) mustEqual expectedJson
      }
    }
  }

  "declarable" - {

    val record = GoodsItemRecord(
      recordId = UUID.randomUUID().toString,
      goodsItem = GoodsItem(
        eori = "eori",
        actorId = "actorId",
        traderRef = "traderRef",
        comcode = "comcode",
        goodsDescription = "goodsDescription",
        countryOfOrigin = "countryOfOrigin",
        category = Category.Controlled,
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
        toReview = true,
        declarable = Some("IMMI ready"),
        reviewReason = Some("reviewReason"),
        srcSystemName = "MDTP",
        updatedDateTime = clock.instant(),
        createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
      )
    )

    "when the record is active" - {

      "and toReview is false" - {

        "and the category is Standard" - {

          "and the current date is before the effective period" - {

            "must set declarable to NotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 6,
                  category = Category.Standard,
                  comcodeEffectiveFromDate = clock.instant().plus(1, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = None
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.NotReady
            }
          }

          "and the current date is after the effective period" - {

            "must set declarable to NotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 6,
                  category = Category.Standard,
                  comcodeEffectiveFromDate = clock.instant().minus(2, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = Some(clock.instant().minus(1, ChronoUnit.DAYS))
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.NotReady
            }
          }

          "and the current date is during the effective period" - {

            "and the commodity code is at least 6 digits" - {

              "must set declarable to ImmiReady" in {

                val record2 = record.copy(
                  goodsItem = record.goodsItem.copy(
                    comcode = "a" * 6,
                    category = Category.Standard,
                    comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
                    comcodeEffectiveToDate = None
                  ),
                  metadata = record.metadata.copy(
                    toReview = false,
                    active = true
                  )
                )

                record2.declarable(clock.instant()) mustEqual Declarable.ImmiReady
              }
            }

            "and the commodity code is less than 6 digits" - {

              "must set declarable to NotReady" in {

                val record2 = record.copy(
                  goodsItem = record.goodsItem.copy(
                    comcode = "a" * 5,
                    category = Category.Standard,
                    comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
                    comcodeEffectiveToDate = None
                  ),
                  metadata = record.metadata.copy(
                    toReview = false,
                    active = true
                  )
                )

                record2.declarable(clock.instant()) mustEqual Declarable.NotReady
              }
            }
          }
        }

        "when the category is Controlled" - {

          "and the current date is before the effective period" - {

            "must set declarable to NotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 8,
                  category = Category.Controlled,
                  comcodeEffectiveFromDate = clock.instant().plus(1, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = None
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.NotReady
            }
          }

          "and the current date is after the effective period" - {

            "must set declarable to NotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 8,
                  category = Category.Controlled,
                  comcodeEffectiveFromDate = clock.instant().minus(2, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = Some(clock.instant().minus(1, ChronoUnit.DAYS))
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.NotReady
            }
          }

          "and the current date is during the effective period" - {

            "and the commodity code is at least 8 digits" - {

              "must set declarable to ImmiReady" in {

                val record2 = record.copy(
                  goodsItem = record.goodsItem.copy(
                    comcode = "a" * 8,
                    category = Category.Controlled,
                    comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
                    comcodeEffectiveToDate = None
                  ),
                  metadata = record.metadata.copy(
                    toReview = false,
                    active = true
                  )
                )

                record2.declarable(clock.instant()) mustEqual Declarable.ImmiReady
              }
            }

            "and the commodity code is less than 8 digits" - {

              "must set declarable to NotReady" in {

                val record2 = record.copy(
                  goodsItem = record.goodsItem.copy(
                    comcode = "a" * 7,
                    category = Category.Controlled,
                    comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
                    comcodeEffectiveToDate = None
                  ),
                  metadata = record.metadata.copy(
                    toReview = false,
                    active = true
                  )
                )

                record2.declarable(clock.instant()) mustEqual Declarable.NotReady
              }
            }
          }
        }

        "when the category is Excluded" - {

          "and the current date is before the effective period" - {

            "must set declarable to NotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 8,
                  category = Category.Excluded,
                  comcodeEffectiveFromDate = clock.instant().plus(1, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = None
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.NotReady
            }
          }

          "and the current date is after the effective period" - {

            "must set declarable to NotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 8,
                  category = Category.Excluded,
                  comcodeEffectiveFromDate = clock.instant().minus(2, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = Some(clock.instant().minus(1, ChronoUnit.DAYS))
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.NotReady
            }
          }

          "and the current date is during the effective period" - {

            "must set declarable to ImmiNotReady" in {

              val record2 = record.copy(
                goodsItem = record.goodsItem.copy(
                  comcode = "a" * 8,
                  category = Category.Excluded,
                  comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
                  comcodeEffectiveToDate = None
                ),
                metadata = record.metadata.copy(
                  toReview = false,
                  active = true
                )
              )

              record2.declarable(clock.instant()) mustEqual Declarable.ImmiNotReady
            }
          }
        }
      }
    }

    "and toReview is true" - {

      "must set declarable to NotReady" in {

        val record2 = record.copy(
          goodsItem = record.goodsItem.copy(
            comcode = "a" * 6,
            category = Category.Standard,
            comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
            comcodeEffectiveToDate = None
          ),
          metadata = record.metadata.copy(
            toReview = true,
            active = true
          )
        )

        record2.declarable(clock.instant()) mustEqual Declarable.NotReady
      }
    }

    "when the record is not active" - {

      "must set the declarable to NotReady" in {

        val record2 = record.copy(
          goodsItem = record.goodsItem.copy(
            comcode = "a" * 6,
            category = Category.Standard,
            comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
            comcodeEffectiveToDate = None
          ),
          metadata = record.metadata.copy(
            toReview = false,
            active = false
          )
        )

        record2.declarable(clock.instant()) mustEqual Declarable.NotReady
      }
    }
  }
}
