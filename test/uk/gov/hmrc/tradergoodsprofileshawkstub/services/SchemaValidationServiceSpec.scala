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

package uk.gov.hmrc.tradergoodsprofileshawkstub.services

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.SchemaValidationService.SchemaValidationError

class SchemaValidationServiceSpec extends AnyFreeSpec with Matchers with OptionValues with GuiceOneAppPerSuite {

  private lazy val validatorService = app.injector.instanceOf[SchemaValidationService]

  private lazy val testSchema = validatorService.createSchema("schemas/example-schema.json").get

  "validate" - {

    "must return an empty list of errors when an object passes validation" in {

      val json = Json.obj(
        "name" -> Json.obj(
          "firstName" -> "foo",
          "lastName" -> "bar",
        ),
        "age" -> 20
      )

      validatorService.validate(testSchema, json) mustBe empty
    }

    "must return errors when an object fails validation" in {

      val json = Json.obj(
        "name" -> Json.obj()
      )

      val expectedErrors = Seq(
        SchemaValidationError("$", "required key [age] not found"),
        SchemaValidationError("$.name", "required key [firstName] not found"),
        SchemaValidationError("$.name", "required key [lastName] not found"),
      )

      validatorService.validate(testSchema, json) mustEqual expectedErrors
    }
  }

  "createSchema" - {

    "must return a schema when the path given is a valid schema" in {
      val result = validatorService.createSchema("/schemas/example-schema.json")
      result.isSuccess mustBe true
    }

    "must fail when there is no file at the given path" in {
      val result = validatorService.createSchema("/schemas/doesnt-exist.json")
      result.isSuccess mustBe false
    }

    "must fail when there is an invalid schema at the given path" in {
      val result = validatorService.createSchema("/schemas/invalid-schema.json")
      result.isSuccess mustBe false
    }
  }
}
