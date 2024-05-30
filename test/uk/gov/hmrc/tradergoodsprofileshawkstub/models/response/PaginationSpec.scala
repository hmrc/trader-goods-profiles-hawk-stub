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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models.response

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.Pagination

class PaginationSpec extends AnyFreeSpec with Matchers {

  "apply" - {

    "must return `None` for `previousPage` when `currentPage` is 0" in {

      val expected = Pagination(
        totalRecords = 10,
        currentPage = 0,
        totalPages = 10,
        nextPage = Some(1),
        previousPage = None
      )

      Pagination(totalRecords = 10, page = 0, size = 1) mustEqual expected
    }

    "must return `None` for `nextPage` when this is the last page" in {

      val expected = Pagination(
        totalRecords = 10,
        currentPage = 9,
        totalPages = 10,
        nextPage = None,
        previousPage = Some(8)
      )

      Pagination(totalRecords = 10, page = 9, size = 1) mustEqual expected
    }

    "must return the last valid page for previousPage if page is greater than the last page" in {

      val expected = Pagination(
        totalRecords = 1,
        currentPage = 9,
        totalPages = 1,
        nextPage = None,
        previousPage = Some(0)
      )

      Pagination(totalRecords = 1, page = 9, size = 1) mustEqual expected
    }
  }
}
