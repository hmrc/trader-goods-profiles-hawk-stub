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

import org.apache.pekko.Done
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
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.MaintainTraderProfileRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future

class TraderProfilesControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues {

  private val clock           = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val mockRepository  = mock[TraderProfileRepository]
  private val mockUuidService = mock[UuidService]

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val formattedDate    = clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "expected-auth-header" -> "some-token"
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[UuidService].toInstance(mockUuidService),
        bind[TraderProfileRepository].toInstance(mockRepository)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRepository)
  }

  "maintainProfile" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val requestBody   = MaintainTraderProfileRequest(
      eori = "eori1234567890",
      actorId = "actorId1234567",
      ukimsNumber = Some("1" * 32),
      nirmsNumber = Some("2" * 13),
      niphlNumber = Some("3" * 8)
    )

    "must update the trader profile when given a valid request" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

      when(mockRepository.upsert(any)).thenReturn(Future.successful(Done))

      val result = route(app, request).value

      status(result) mustEqual OK

      contentAsJson(result) mustEqual Json.toJson(requestBody)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).upsert(requestBody)
    }

    "must not update the trader profile and return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-token"
        )

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

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
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

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when there is no date header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
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

      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
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

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when there is no content-type header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
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

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when there is an invalid content-type header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
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

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when the request body can't be parsed as json" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
        .withBody("{")
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
        detail = Seq.empty
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return an error when there are json schema violations" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
        .withBody(Json.obj())
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
          "$: required key [eori] not found",
          "$: required key [actorId] not found"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).upsert(any)
    }

    "must not update the trader profile and return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.TraderProfilesController.maintainProfile())
        .withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type"     -> "application/json",
          "Accept"           -> "application/json",
          "Date"             -> formattedDate,
          "Authorization"    -> "some-other-token"
        )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).upsert(any)
    }
  }
}
