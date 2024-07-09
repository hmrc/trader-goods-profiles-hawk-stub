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

import enumeratum.{EnumEntry, PlayEnum}

sealed abstract class Declarable(override val entryName: String) extends EnumEntry

object Declarable extends PlayEnum[Declarable] {

  override def values: IndexedSeq[Declarable] = findValues

  case object ImmiReady extends Declarable("IMMI Ready")
  case object ImmiNotReady extends Declarable("Not Ready For IMMI")
  case object NotReady extends Declarable("Not Ready For Use")
}
