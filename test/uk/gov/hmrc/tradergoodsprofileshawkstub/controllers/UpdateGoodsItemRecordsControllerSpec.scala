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

package uk.gov.hmrc.tradergoodsprofileshawkstub.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.tradergoodsprofileshawkstub.config.AppConfig
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.{PatchGoodsItemRecordRequest, UpdateGoodsItemRecordRequest}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository.{DuplicateEoriAndTraderRefException, RecordInactiveException, RecordLockedException}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.{GoodsItemRecordRepository, TraderProfileRepository}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future

class UpdateGoodsItemRecordsControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues {

  private val clock                        = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val mockGoodsItemRepository      = mock[GoodsItemRecordRepository]
  private val mockTraderProfilesRepository = mock[TraderProfileRepository]
  private val mockUuidService              = mock[UuidService]
  private val appConfig                    = mock[AppConfig]

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val formattedDate    = clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)
  private val correlationId    = UUID.randomUUID().toString
  private val forwardedHost    = "forwarded-for"
  private val record           = generateRecord

  private val patchRequestBody = PatchGoodsItemRecordRequest(
    recordId = record.recordId,
    eori = record.goodsItem.eori,
    actorId = record.goodsItem.actorId,
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

  private val profile = TraderProfile(
    eori = patchRequestBody.eori,
    actorId = patchRequestBody.actorId,
    nirmsNumber = None,
    niphlNumber = None,
    ukimsNumber = None,
    lastUpdated = clock.instant()
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "expected-auth-header"            -> "some-token",
        "goods-item-records.default-size" -> 1337,
        "goods-item-records.max-size"     -> 1338
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[GoodsItemRecordRepository].toInstance(mockGoodsItemRepository),
        bind[TraderProfileRepository].toInstance(mockTraderProfilesRepository),
        bind[UuidService].toInstance(mockUuidService),
        bind[AppConfig].toInstance(appConfig)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](mockGoodsItemRepository, mockUuidService, mockTraderProfilesRepository, appConfig)
    when(appConfig.isPutMethodEnabled).thenReturn(true)
  }

  "patchRecord" - {

    "must update a record and return the relevant response when given a valid request" in {
      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.patchRecord(any)).thenReturn(Future.successful(Some(record)))

      val result = route(app, request).value

      status(result) mustEqual OK

      contentAsJson(result) mustEqual record.toGetRecordResponse(profile, clock.instant())
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(patchRequestBody.eori)
      verify(mockGoodsItemRepository).patchRecord(patchRequestBody)
    }

    "must not update a record and return an error when the record does not exist in the database" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.patchRecord(any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 026, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(patchRequestBody.eori)
      verify(mockGoodsItemRepository).patchRecord(patchRequestBody)
    }

    "must not update a record and return an error when there is no profile matching the eori" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )
      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 007, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(patchRequestBody.eori)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when the updated traderRef is not unique within the database" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.patchRecord(any)).thenReturn(Future.failed(DuplicateEoriAndTraderRefException))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 010, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(patchRequestBody.eori)
      verify(mockGoodsItemRepository).patchRecord(patchRequestBody)
    }

    "must not update a record and return an error when the record is inactive" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.patchRecord(any)).thenReturn(Future.failed(RecordInactiveException))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 031, message: Invalid Request"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(patchRequestBody.eori)
      verify(mockGoodsItemRepository).patchRecord(patchRequestBody)
    }

    "must not update a record and return an error when the record is locked" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.patchRecord(any)).thenReturn(Future.failed(RecordLockedException))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 027, message: Invalid Request"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(patchRequestBody.eori)
      verify(mockGoodsItemRepository).patchRecord(patchRequestBody)
    }

    "must not update a record and return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 001, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, times(1)).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 005, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result) mustBe empty
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when there is no date header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 002, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(patchRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Date"             -> "invalid",
          "Accept"           -> "application/json",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 002, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when there is no content-type header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when there is an invalid content-type header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "text/xml",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when the request body can't be parsed as json" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody("{")
        .withHeaders(
          "Content-Type"     -> "application/json",
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      // Unsure if this is the correct response as it's not documented
      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Invalid message : Bad Request",
        source = "Json Validation",
        detail = Seq.empty
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return an error when there are json schema violations" in {

      val invalidRequestBody = patchRequestBody.copy(
        eori = "eori12345678901234567890",
        actorId = "actorId12345678901234567890"
      )

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withBody(Json.toJson(invalidRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Invalid message : Bad Request",
        source = "Json Validation",
        detail = Seq(
          "$.eori: expected maxLength: 17, actual: 24",
          "$.actorId: expected maxLength: 17, actual: 27"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "text/xml",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must not update a record and return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.patchRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "text/xml",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-other-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }
  }

  "putRecord" - {

    val requestBody = UpdateGoodsItemRecordRequest(
      recordId = record.recordId,
      eori = record.goodsItem.eori,
      actorId = record.goodsItem.actorId,
      traderRef = record.goodsItem.traderRef,
      comcode = record.goodsItem.comcode,
      goodsDescription = record.goodsItem.goodsDescription,
      countryOfOrigin = record.goodsItem.countryOfOrigin,
      category = record.goodsItem.category,
      assessments = record.goodsItem.assessments,
      supplementaryUnit = record.goodsItem.supplementaryUnit,
      measurementUnit = record.goodsItem.measurementUnit,
      comcodeEffectiveFromDate = record.goodsItem.comcodeEffectiveFromDate,
      comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate
    )

    val profile = TraderProfile(
      eori = requestBody.eori,
      actorId = requestBody.actorId,
      nirmsNumber = None,
      niphlNumber = None,
      ukimsNumber = None,
      lastUpdated = clock.instant()
    )

    "must update a record and return the relevant response when given a valid request" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.updateRecord(any)).thenReturn(Future.successful(Some(record)))

      val result = route(app, request).value

      status(result) mustEqual OK

      contentAsJson(result) mustEqual record.toGetRecordResponse(profile, clock.instant())
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(requestBody.eori)
      verify(mockGoodsItemRepository).updateRecord(requestBody)
    }

    "must not update a record and return an error when the record does not exist in the database" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.updateRecord(any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 026, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(requestBody.eori)
      verify(mockGoodsItemRepository).updateRecord(requestBody)
    }

    "must not update a record and return an error when there is no profile matching the eori" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 007, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(requestBody.eori)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when the updated traderRef is not unique within the database" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.updateRecord(any)).thenReturn(Future.failed(DuplicateEoriAndTraderRefException))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 010, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(requestBody.eori)
      verify(mockGoodsItemRepository).updateRecord(requestBody)
    }

    "must not update a record and return an error when the record is inactive" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.updateRecord(any)).thenReturn(Future.failed(RecordInactiveException))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 031, message: Invalid Request"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(requestBody.eori)
      verify(mockGoodsItemRepository).updateRecord(requestBody)
    }

    "must not update a record and return an error when the record is locked" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.updateRecord(any)).thenReturn(Future.failed(RecordLockedException))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 027, message: Invalid Request"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(requestBody.eori)
      verify(mockGoodsItemRepository).updateRecord(requestBody)
    }

    "must not update a record and return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 001, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, times(1)).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 005, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result) mustBe empty
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when there is no date header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 002, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Date"             -> "invalid",
          "Accept"           -> "application/json",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 002, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when there is no content-type header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when there is an invalid content-type header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "text/xml",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when the request body can't be parsed as json" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody("{")
        .withHeaders(
          "Content-Type"     -> "application/json",
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      // Unsure if this is the correct response as it's not documented
      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Invalid message : Bad Request",
        source = "Json Validation",
        detail = Seq.empty
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return an error when there are json schema violations" in {

      val invalidRequestBody = requestBody.copy(
        eori = "eori12345678901234567890",
        actorId = "actorId12345678901234567890"
      )

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withBody(Json.toJson(invalidRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Invalid message : Bad Request",
        source = "Json Validation",
        detail = Seq(
          "$.eori: expected maxLength: 17, actual: 24",
          "$.actorId: expected maxLength: 17, actual: 27"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "text/xml",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

    "must not update a record and return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.UpdateGoodsItemRecordsController.putRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "text/xml",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-other-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).updateRecord(any)
    }

  }

  private def generateRecord = GoodsItemRecord(
    recordId = UUID.randomUUID().toString,
    goodsItem = GoodsItem(
      eori = "eori1234567890",
      actorId = "actorId1234567",
      traderRef = "traderRef",
      comcode = "comcode",
      goodsDescription = "goodsDescription",
      countryOfOrigin = "GB",
      category = Some(Category.Controlled),
      assessments = Some(
        Seq(
          Assessment(
            assessmentId = Some("assessmentId"),
            primaryCategory = Some(Category.Controlled),
            condition = Some(
              Condition(
                `type` = Some("type"),
                conditionId = Some("1234567890"),
                conditionDescription = Some("conditionDescription"),
                conditionTraderText = Some("conditionTraderText")
              )
            )
          )
        )
      ),
      supplementaryUnit = Some(BigDecimal(2.5)),
      measurementUnit = Some("measurementUnit"),
      comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS),
      comcodeEffectiveToDate = Some(clock.instant().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
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
}
