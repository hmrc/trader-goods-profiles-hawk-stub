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

package uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses

import play.api.libs.json.{Json, JsonConfiguration, OFormat, OptionHandlers}

final case class Pagination(
  totalRecords: Int,
  currentPage: Int,
  totalPages: Int,
  nextPage: Option[Int],
  previousPage: Option[Int]
)

object Pagination {

  implicit lazy val format: OFormat[Pagination] = {
    implicit val config: JsonConfiguration = JsonConfiguration(optionHandlers = OptionHandlers.WritesNull)
    Json.format
  }

  def apply(totalRecords: Int, page: Int, size: Int): Pagination = {

    val totalPages = (totalRecords.toDouble / size).ceil.toInt

    Pagination(
      totalRecords = totalRecords,
      currentPage = page,
      totalPages = totalPages,
      nextPage = Option.when(page < totalPages - 1)(page + 1),
      previousPage = Option.when(page > 0)(page.min(totalRecords) - 1)
    )
  }
}
