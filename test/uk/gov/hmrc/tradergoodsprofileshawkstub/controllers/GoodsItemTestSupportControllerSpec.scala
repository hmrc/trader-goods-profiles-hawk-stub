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
import org.mockito.Mockito.{times, verify, when}
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
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.Declarable
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.PatchGoodsItemRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository

import scala.concurrent.Future

class GoodsItemTestSupportControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues {

  private val mockRepository = mock[GoodsItemRecordRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[GoodsItemRecordRepository].toInstance(mockRepository),
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRepository)
  }

  ".patch" - {

    "must patch a record and return OK when the patch succeeds in the database" in {

      when(mockRepository.patch(any)).thenReturn(Future.successful(Some(Done)))

      val patchRequest = PatchGoodsItemRequest("eori", "recordId", None, Some(123), None, None, None, None, None, None)

      val request = FakeRequest(routes.GoodsItemTestSupportController.patch())
        .withJsonBody(Json.toJson(patchRequest))
        .withHeaders("Content-Type" -> "application/json")

      val result = route(app, request).value

      status(result) mustEqual OK
      verify(mockRepository, times(1)).patch(patchRequest)
    }

    "must patch a record and return OK when the patch succeeds in the database with declarable" in {

      when(mockRepository.patch(any)).thenReturn(Future.successful(Some(Done)))

      val patchRequest = PatchGoodsItemRequest("eori", "recordId", None, Some(123), None, None, None, Some(Declarable.ImmiReady), None, None)

      val request = FakeRequest(routes.GoodsItemTestSupportController.patch())
        .withJsonBody(Json.toJson(patchRequest))
        .withHeaders("Content-Type" -> "application/json")

      val result = route(app, request).value

      status(result) mustEqual OK
      verify(mockRepository, times(1)).patch(patchRequest)
    }

    "must return Not Found when a record doesn't exist to patch" in {

      when(mockRepository.patch(any)).thenReturn(Future.successful(None))

      val patchRequest = PatchGoodsItemRequest("eori", "recordId", None, Some(123), None, None, None, None, None, None)

      val request = FakeRequest(routes.GoodsItemTestSupportController.patch())
        .withJsonBody(Json.toJson(patchRequest))
        .withHeaders("Content-Type" -> "application/json")

      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
      verify(mockRepository, times(1)).patch(patchRequest)
    }

    "must return Bad Request when the payload cannot be parsed" in {

      val invalidPayload = Json.obj()

      val request = FakeRequest(routes.GoodsItemTestSupportController.patch())
        .withJsonBody(Json.toJson(invalidPayload))
        .withHeaders("Content-Type" -> "application/json")

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
    }
  }
}
