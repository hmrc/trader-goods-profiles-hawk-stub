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

import enumeratum.values.{IntEnumEntry, IntPlayEnum}

sealed abstract class Category(val value: Int, val name: String) extends IntEnumEntry

object Category extends IntPlayEnum[Category] {

  override def values: IndexedSeq[Category] = findValues

  case object Excluded extends Category(1, "Excluded")
  case object Controlled extends Category(2, "Controlled")
  case object Standard extends Category(3, "Standard")
}
