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
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.TraderProfile
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future


class GetProfileControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val correlationId = UUID.randomUUID().toString
  private val mockTraderProfileRepository = mock[TraderProfileRepository]
  private val mockUuidService = mock[UuidService]

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val formattedDate = clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "expected-auth-header" -> "some-token",
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[TraderProfileRepository].toInstance(mockTraderProfileRepository),
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()


  override def beforeEach(): Unit = {
    super.beforeEach()

    Mockito.reset[Any](mockTraderProfileRepository, mockUuidService)
    when(mockUuidService.generate()).thenReturn(correlationId)
  }

  "getProfile" - {

    val forwardedHost = "forwarded-for"
    val eori = "eori1234567890"
    val actorId = "actorId1234567"
    val profile = TraderProfile(
      eori,
      actorId,
      None,
      None,
      None,
      clock.instant())

    "should return a successful response" in {
      when(mockTraderProfileRepository.get(any)).thenReturn(Future.successful(Some(profile)))

      val request = FakeRequest(routes.GetProfileController.getProfile(eori))
        .withHeaders(validHeaders: _*)
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj(
        "eori" -> eori,
        "actorId" -> actorId
      )

      verify(mockTraderProfileRepository).get(eori)
    }

    "should validate headers" in {
      when(mockTraderProfileRepository.get(any)).thenReturn(Future.successful(Some(profile)))

      val request = FakeRequest(routes.GetProfileController.getProfile(eori))
        .withHeaders("Authorization" -> "some-token")
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
      verify(mockTraderProfileRepository, never).get(any)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("Date", result).value mustEqual formattedDate
      header("Content-Type", result).value mustEqual "application/json"
      contentAsJson(result) mustBe Json.obj(
        "errorDetail" -> Json.obj(
          "correlationId" -> correlationId,
          "timestamp" -> clock.instant(),
          "errorCode" -> "400",
          "errorMessage" -> "Bad Request",
          "source" -> "BACKEND",
          "sourceFaultDetail" -> Json.obj(
            "detail" -> Json.arr(
              "error: 001, message: Invalid Header",
              "error: 005, message: Invalid Header",
              "error: 002, message: Invalid Header",
              "error: 004, message: Invalid Header"
            )
          )
        )
      )
    }

    "should validate authorization header when this is missing" in {
      when(mockTraderProfileRepository.get(any)).thenReturn(Future.successful(Some(profile)))

      val request = FakeRequest(routes.GetProfileController.getProfile(eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate
        )
      val result = route(app, request).value

      status(result) mustBe FORBIDDEN
      verify(mockTraderProfileRepository, never).get(any)
    }

    "should validate authorization header when this is invalid" in {
      when(mockTraderProfileRepository.get(any)).thenReturn(Future.successful(Some(profile)))

      val request = FakeRequest(routes.GetProfileController.getProfile(eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "invalid-header"
        )
      val result = route(app, request).value

      status(result) mustBe FORBIDDEN
      verify(mockTraderProfileRepository, never).get(any)
    }

    "should return an error eori does not exist in database" in {
      when(mockTraderProfileRepository.get(any)).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.GetProfileController.getProfile(eori))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "errorDetail" -> Json.obj(
          "correlationId" -> correlationId,
          "timestamp" -> clock.instant(),
          "errorCode" -> "400",
          "errorMessage" -> "Bad Request",
          "source" -> "BACKEND",
          "sourceFaultDetail" -> Json.obj(
            "detail" -> Json.arr(
              "error: 007, message: Invalid Request Parameter"
            )
          )
        )
      )
    }

    def validHeaders: Seq[(String, String)] =
      Seq(
        "X-Correlation-ID" -> correlationId,
        "X-Forwarded-Host" -> forwardedHost,
        "Accept" -> "application/json",
        "Date" -> formattedDate,
        "Authorization" -> "some-token"
      )
  }
}
