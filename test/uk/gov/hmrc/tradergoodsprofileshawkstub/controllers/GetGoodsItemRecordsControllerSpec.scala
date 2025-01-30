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
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.{GetGoodsItemsResponse, Pagination}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.{GoodsItemRecordRepository, TraderProfileRepository}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.Future

class GetGoodsItemRecordsControllerSpec
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

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val formattedDate    = clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)

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
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](mockGoodsItemRepository, mockUuidService, mockTraderProfilesRepository)
  }

  "getRecord" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val record        = generateRecord

    val profile = TraderProfile(
      eori = record.goodsItem.eori,
      actorId = record.goodsItem.actorId,
      nirmsNumber = None,
      niphlNumber = None,
      ukimsNumber = None,
      lastUpdated = clock.instant()
    )

    "must return a response with a single element when a result is returned" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.getById(any, any)).thenReturn(Future.successful(Some(record)))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(
        GetGoodsItemsResponse(
          goodsItemRecords = Seq(record),
          pagination = Pagination(totalRecords = 1, page = 0, size = 1)
        )
      )(GetGoodsItemsResponse.writes(profile, clock.instant()))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository).getById(record.goodsItem.eori, record.recordId)
    }

    "must return a 400 with error 026 when a result is not returned" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.getById(any, any)).thenReturn(Future.successful(None))

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
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository).getById(record.goodsItem.eori, record.recordId)
    }

    "must return an error when there is no profile for the eori" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(profile.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
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
      verify(mockTraderProfilesRepository).get(profile.eori)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, times(1)).generate()
      verify(mockTraderProfilesRepository, never).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository, never).getById(any, any)
    }

    "must return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result) mustBe empty
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository, never).getById(any, any)
    }

    "must return an error when there is no date header" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository, never).getById(any, any)
    }

    "must return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> "invalid",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository, never).getById(any, any)
    }

    "must return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate
        )

      when(mockGoodsItemRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository, never).getById(any, any)
    }

    "must return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.GetGoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-other-token"
        )

      when(mockGoodsItemRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository, never).getById(any, any)
    }
  }

  "getRecords" - {

    val correlationId         = UUID.randomUUID().toString
    val forwardedHost         = "forwarded-for"
    val record                = generateRecord
    val lastUpdatedTime       = LocalDateTime.of(2024, 3, 2, 12, 30, 45).toInstant(ZoneOffset.UTC)
    val lastUpdatedTimeString = "2024-03-02T12:30:45Z"

    val profile = TraderProfile(
      eori = record.goodsItem.eori,
      actorId = record.goodsItem.actorId,
      nirmsNumber = None,
      niphlNumber = None,
      ukimsNumber = None,
      lastUpdated = clock.instant()
    )

    def url(
      eori: String,
      page: Option[String] = None,
      size: Option[String] = None,
      lastUpdatedDate: Option[String] = None
    ): Call = {

      val params = List(
        page.map(p => s"page=$p"),
        size.map(s => s"size=$s"),
        lastUpdatedDate.map(d => s"lastUpdatedDate=$d")
      ).flatten.mkString("?", "&", "")

      val base = routes.GetGoodsItemRecordsController.getRecords(eori).url

      Call(GET, s"$base$params")
    }

    "must return a response with elements when results are returned" in {

      val request = FakeRequest(
        url(record.goodsItem.eori, page = Some("0"), size = Some("1338"), lastUpdatedDate = Some(lastUpdatedTimeString))
      )
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 7,
        records = Seq(record)
      )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(
        GetGoodsItemsResponse(
          goodsItemRecords = Seq(record),
          pagination = Pagination(totalRecords = 7, page = 0, size = 1338)
        )
      )(GetGoodsItemsResponse.writes(profile, clock.instant()))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository)
        .get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 0, size = 1338)
    }

    "must return an empty response when a result is not returned" in {

      val request = FakeRequest(
        url(record.goodsItem.eori, page = Some("2"), size = Some("3"), lastUpdatedDate = Some(lastUpdatedTimeString))
      )
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 0,
        records = Seq.empty
      )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(
        GetGoodsItemsResponse(
          goodsItemRecords = Seq.empty,
          pagination = Pagination(totalRecords = 0, page = 2, size = 3)
        )
      )(GetGoodsItemsResponse.writes(profile, clock.instant()))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository)
        .get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 2, size = 3)
    }

    "must default page to 0" in {

      val request =
        FakeRequest(url(record.goodsItem.eori, size = Some("3"), lastUpdatedDate = Some(lastUpdatedTimeString)))
          .withHeaders(
            "X-Correlation-ID" -> correlationId,
            "X-Forwarded-Host" -> forwardedHost,
            "Accept"           -> "application/json",
            "Date"             -> formattedDate,
            "Authorization"    -> "some-token"
          )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 0,
        records = Seq.empty
      )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(
        GetGoodsItemsResponse(
          goodsItemRecords = Seq.empty,
          pagination = Pagination(totalRecords = 0, page = 0, size = 3)
        )
      )(GetGoodsItemsResponse.writes(profile, clock.instant()))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository)
        .get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 0, size = 3)
    }

    "must default size to the size provided in configuration" in {
      val request =
        FakeRequest(url(record.goodsItem.eori, page = Some("2"), lastUpdatedDate = Some(lastUpdatedTimeString)))
          .withHeaders(
            "X-Correlation-ID" -> correlationId,
            "X-Forwarded-Host" -> forwardedHost,
            "Accept"           -> "application/json",
            "Date"             -> formattedDate,
            "Authorization"    -> "some-token"
          )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 0,
        records = Seq.empty
      )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(
        GetGoodsItemsResponse(
          goodsItemRecords = Seq.empty,
          pagination = Pagination(totalRecords = 0, page = 2, size = 1337)
        )
      )(GetGoodsItemsResponse.writes(profile, clock.instant()))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository)
        .get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 2, size = 1337)
    }

    "must default lastUpdatedDate to None" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("2"), size = Some("3")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 7,
        records = Seq(record)
      )

      when(mockTraderProfilesRepository.get(any)).thenReturn(Future.successful(Some(profile)))
      when(mockGoodsItemRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(
        GetGoodsItemsResponse(
          goodsItemRecords = Seq(record),
          pagination = Pagination(totalRecords = 7, page = 2, size = 3)
        )
      )(GetGoodsItemsResponse.writes(profile, clock.instant()))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository).get(record.goodsItem.eori)
      verify(mockGoodsItemRepository).get(record.goodsItem.eori, lastUpdated = None, page = 2, size = 3)
    }

    "must return an error when there is no profile for the eori" in {

      val request = FakeRequest(
        url(record.goodsItem.eori, page = Some("2"), size = Some("3"), lastUpdatedDate = Some(lastUpdatedTimeString))
      )
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
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
      verify(mockTraderProfilesRepository).get(profile.eori)
      verify(mockGoodsItemRepository, never).patchRecord(any)
    }

    "must return an error when page is invalid" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("page")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 029, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when page is less than 0" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("-1")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 029, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when size is invalid" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("size")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 030, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when size is less than 0" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("-1")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 030, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when size is greater than the configured max size" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("1339")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 030, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when lastUpdated is invalid" in {

      val request = FakeRequest(url(record.goodsItem.eori, lastUpdatedDate = Some("lastUpdatedDate")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 028, message: Invalid Request Parameter"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when there is no correlation-id header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, times(1)).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when there is no forwarded-host header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result) mustBe empty
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when there is no date header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return an error when there is an invalid date header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> "invalid",
          "Authorization"    -> "some-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate
        )

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
    }

    "must return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-other-token"
        )

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockUuidService, never).generate()
      verify(mockTraderProfilesRepository, never).get(any)
      verify(mockGoodsItemRepository, never).get(any, any, any, any)
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
