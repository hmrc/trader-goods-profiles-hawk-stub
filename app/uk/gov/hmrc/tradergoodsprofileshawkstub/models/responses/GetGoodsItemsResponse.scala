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

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.GoodsItemRecord

final case class GetGoodsItemsResponse(
                                        goodsItemRecords: Seq[GoodsItemRecord],
                                        pagination: Pagination
                                      )

object GetGoodsItemsResponse {

  implicit lazy val writes: OWrites[GetGoodsItemsResponse] = OWrites { response =>

    Json.obj(
      "goodsItemRecords" -> response.goodsItemRecords.map(_.toGetRecordResponse),
      "pagination" -> response.pagination
    )
  }
}
