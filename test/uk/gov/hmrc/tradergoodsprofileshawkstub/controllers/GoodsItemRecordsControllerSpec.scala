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
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.CREATED
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.{CreateGoodsItemRecordRequest, UpdateGoodsItemRecordRequest}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.{GetGoodsItemsResponse, Pagination}
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime, ZoneId, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future

class GoodsItemRecordsControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val mockRepository = mock[GoodsItemRecordRepository]
  private val mockUuidService = mock[UuidService]

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val formattedDate = clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "expected-auth-header" -> "some-token",
        "goods-item-records.default-size" -> 1337,
        "goods-item-records.max-size" -> 1338
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[GoodsItemRecordRepository].toInstance(mockRepository),
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRepository, mockUuidService)
  }


  "createRecord" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val record = generateRecord

    val requestBody = CreateGoodsItemRecordRequest(
      eori = record.goodsItem.eori,
      actorId = record.goodsItem.actorId,
      traderRef = record.goodsItem.traderRef,
      comcode = record.goodsItem.comcode,
      goodsDescription = record.goodsItem.goodsDescription,
      countryOfOrigin = record.goodsItem.countryOfOrigin,
      category = record.goodsItem.category,
      assessments = Some(record.goodsItem.assessments),
      supplementaryUnit = record.goodsItem.supplementaryUnit,
      measurementUnit = record.goodsItem.measurementUnit,
      comcodeEffectiveFromDate = record.goodsItem.comcodeEffectiveFromDate,
      comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate
    )

    "must create a record and return the relevant response when given a valid request" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))

      val result = route(app, request).value

      status(result) mustEqual CREATED

      contentAsJson(result) mustEqual record.toCreateRecordResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).insert(requestBody)
    }

    "must not create a record and return an error when the given eori/traderRef is not unique within the database" ignore {
      // TODO
    }

    "must not create a record and return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no date header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Date" -> "invalid",
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no content-type header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is an invalid content-type header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when the request body can't be parsed as json" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody("{")
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

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

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there are json schema violations" in {

      val invalidRequestBody = requestBody.copy(
        eori = "eori12345678901234567890",
        actorId = "actorId12345678901234567890"
      )

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord()).withBody(Json.toJson(invalidRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Invalid message : Bad Request",
        source = "Json Validation",
        detail = Seq(
          "$.actorId: expected maxLength: 17, actual: 27",
          "$.eori: expected maxLength: 17, actual: 24"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no profile matching the eori" ignore {
      // TODO
    }

    "must not create a record and return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-other-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).insert(any)
    }
  }

  "getRecord" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val record = generateRecord

    "must return a response with a single element when a result is returned" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(Some(record)))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq(record),
        pagination = Pagination(totalRecords = 1, page = 0, size = 1)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).getById(record.goodsItem.eori, record.recordId)
    }

    "must return an empty response when a result is not returned" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq.empty,
        pagination = Pagination(totalRecords = 0, page = 0, size = 1)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).getById(record.goodsItem.eori, record.recordId)
    }

    "must return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).getById(any, any)
    }

    "must return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).getById(any, any)
    }

    "must return an error when there is no date header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).getById(any, any)
    }

    "must return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> "invalid",
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).getById(any, any)
    }

    "must return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).getById(any, any)
    }

    "must return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.getRecord(record.goodsItem.eori, record.recordId))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-other-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).getById(any, any)
    }
  }

  "getRecords" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val record = generateRecord
    val lastUpdatedTime = LocalDateTime.of(2024, 3, 2, 12, 30, 45).toInstant(ZoneOffset.UTC)
    val lastUpdatedTimeString = "2024-03-02T12:30:45Z"

    def url(eori: String, page: Option[String] = None, size: Option[String] = None, lastUpdatedDate: Option[String] = None): Call = {

      val params = List(
        page.map(p => s"page=$p"),
        size.map(s => s"size=$s"),
        lastUpdatedDate.map(d => s"lastUpdatedDate=$d")
      ).flatten.mkString("?", "&", "")

      val base = routes.GoodsItemRecordsController.getRecords(eori).url

      Call(GET, s"$base$params")
    }

    "must return a response with elements when results are returned" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("2"), size = Some("3"), lastUpdatedDate = Some(lastUpdatedTimeString)))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 7, records = Seq(record)
      )

      when(mockRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq(record),
        pagination = Pagination(totalRecords = 7, page = 2, size = 3)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 2, size = 3)
    }

    "must return an empty response when a result is not returned" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("2"), size = Some("3"), lastUpdatedDate = Some(lastUpdatedTimeString)))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 0, records = Seq.empty
      )

      when(mockRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq.empty,
        pagination = Pagination(totalRecords = 0, page = 2, size = 3)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 2, size = 3)
    }

    "must default page to 0" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("3"), lastUpdatedDate = Some(lastUpdatedTimeString)))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 0, records = Seq.empty
      )

      when(mockRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq.empty,
        pagination = Pagination(totalRecords = 0, page = 0, size = 3)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 0, size = 3)
    }

    "must default size to the size provided in configuration" in {
      val request = FakeRequest(url(record.goodsItem.eori, page = Some("2"), lastUpdatedDate = Some(lastUpdatedTimeString)))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 0, records = Seq.empty
      )

      when(mockRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq.empty,
        pagination = Pagination(totalRecords = 0, page = 2, size = 1337)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).get(record.goodsItem.eori, lastUpdated = Some(lastUpdatedTime), page = 2, size = 1337)
    }

    "must default lastUpdatedDate to None" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("2"), size = Some("3")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      val goodsItemRecordsResult = GetGoodsItemRecordsResult(
        totalCount = 7, records = Seq(record)
      )

      when(mockRepository.get(any, any, any, any)).thenReturn(Future.successful(goodsItemRecordsResult))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResponse = Json.toJson(GetGoodsItemsResponse(
        goodsItemRecords = Seq(record),
        pagination = Pagination(totalRecords = 7, page = 2, size = 3)
      ))

      contentAsJson(result) mustEqual expectedResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).get(record.goodsItem.eori, lastUpdated = None, page = 2, size = 3)
    }

    "must return an error when page is invalid" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("page")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when page is less than 0" in {

      val request = FakeRequest(url(record.goodsItem.eori, page = Some("-1")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when size is invalid" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("size")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when size is less than 0" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("-1")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when size is greater than the configured max size" in {

      val request = FakeRequest(url(record.goodsItem.eori, size = Some("1339")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when lastUpdated is invalid" in {

      val request = FakeRequest(url(record.goodsItem.eori, lastUpdatedDate = Some("lastUpdatedDate")))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when there is no correlation-id header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when there is no forwarded-host header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when there is no date header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return an error when there is an invalid date header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> "invalid",
          "Authorization" -> "some-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))
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

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).get(any, any, any, any)
    }

    "must return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(url(record.goodsItem.eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-other-token"
        )

      when(mockRepository.getById(any, any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).get(any, any, any, any)
    }
  }

  "updateRecord" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val record = generateRecord

    val requestBody = UpdateGoodsItemRecordRequest(
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

    "must update a record and return the relevant response when given a valid request" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.update(any)).thenReturn(Future.successful(Some(record)))

      val result = route(app, request).value

      status(result) mustEqual OK

      contentAsJson(result) mustEqual record.toCreateRecordResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).update(requestBody)
    }

    "must not update a record and return an error when the record does not exist in the database" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.update(any)).thenReturn(Future.successful(None))

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          // TODO what should this actually be?
          "error: XXX, message: Record does not exist"
        )
      )

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).update(requestBody)
    }

    "must not update a record and return an error when the updated traderRef is not unique within the database" ignore {
      // TODO
    }

    "must not update a record and return an error when the record is inactive" ignore {
      // TODO
    }

    "must not update a record and return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there is no date header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Date" -> "invalid",
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there is no content-type header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there is an invalid content-type header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when the request body can't be parsed as json" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody("{")
        .withHeaders(
          "Content-Type" -> "application/json",
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there are json schema violations" in {

      val invalidRequestBody = requestBody.copy(
        eori = "eori12345678901234567890",
        actorId = "actorId12345678901234567890"
      )

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord()).withBody(Json.toJson(invalidRequestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
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

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return an error when there is no profile matching the eori" ignore {
      // TODO
    }

    "must not update a record and return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).update(any)
    }

    "must not update a record and return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.GoodsItemRecordsController.updateRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-other-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).update(any)
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
      category = 1,
      assessments = Seq(
        Assessment(
          assessmentId = Some("assessmentId"),
          primaryCategory = Some(2),
          condition = Some(Condition(
            `type` = Some("type"),
            conditionId = Some("1234567890"),
            conditionDescription = Some("conditionDescription"),
            conditionTraderText = Some("conditionTraderText")
          ))
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
      reviewReason = None,
      declarable = "declarable",
      ukimsNumber = None,
      nirmsNumber = None,
      niphlNumber = None,
      srcSystemName = "MDTP",
      updatedDateTime = clock.instant(),
      createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
    )
  )
}
