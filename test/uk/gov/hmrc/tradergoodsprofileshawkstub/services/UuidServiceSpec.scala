/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import java.util.UUID

class UuidServiceSpec extends AnyFreeSpec with Matchers {

  private val service = new UuidService()

  "UuidService" - {

    "generate" - {

      "must return a valid UUID string" in {
        val result = service.generate()

        // Check it's not empty
        result mustNot be(empty)

        // Check it's a valid UUID format (will throw IllegalArgumentException if not)
        UUID.fromString(result).toString mustBe result
      }

      "must return unique values on subsequent calls" in {
        val uuid1 = service.generate()
        val uuid2 = service.generate()

        uuid1 mustNot be(uuid2)
      }
    }
  }
}
