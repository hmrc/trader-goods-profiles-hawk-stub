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

import java.time.{Clock, Instant, ZoneOffset}

class ErrorResponseSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

  "must write to correct json" in {

    val model = ErrorResponse(
      correlationId = "correlationId",
      timestamp = clock.instant(),
      errorCode = "errorCode",
      errorMessage = "errorMessage",
      source = "source",
      detail = Seq("error1", "error2")
    )

    val expectedJson = Json.obj(
      "errorDetail" -> Json.obj(
        "correlationId"     -> "correlationId",
        "timestamp"         -> clock.instant(),
        "errorCode"         -> "errorCode",
        "errorMessage"      -> "errorMessage",
        "source"            -> "source",
        "sourceFaultDetail" -> Json.obj(
          "detail" -> Json.arr("error1", "error2")
        )
      )
    )

    Json.toJson(model) mustEqual expectedJson
  }
}
