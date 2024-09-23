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

import org.everit.json.schema.loader.SchemaLoader
import org.everit.json.schema.{Schema, ValidationException}
import org.json.{JSONObject, JSONTokener}
import play.api.Environment
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.SchemaValidationService.SchemaValidationError

import javax.inject.{Inject, Singleton}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

@Singleton
class SchemaValidationService @Inject() (
  environment: Environment
) {

  def validate(schema: Schema, input: JsValue): Seq[SchemaValidationError] =
    try {
      val json = new JSONObject(Json.stringify(input))
      schema.validate(json)
      Seq.empty
    } catch {
      case e: ValidationException =>
        accumulateErrors(e)
    }

  private def accumulateErrors(e: ValidationException): Seq[SchemaValidationError] = {

    @tailrec
    def traverse(
      accumulator: Seq[ValidationException],
      exceptions: Seq[ValidationException]
    ): Seq[ValidationException] =
      if (exceptions.isEmpty) {
        accumulator
      } else {
        val head = exceptions.head
        val tail = exceptions.tail
        if (head.getCausingExceptions.isEmpty) {
          traverse(accumulator :+ head, tail)
        } else {
          traverse(accumulator, head.getCausingExceptions.asScala.toSeq ++ tail)
        }
      }

    def formatPointer(pointer: String): String =
      pointer
        .replaceAll("^#", "\\$")
        .replaceAll("/", ".")

    traverse(Seq.empty, Seq(e)).map { e =>
      SchemaValidationError(formatPointer(e.getPointerToViolation), e.getErrorMessage)
    }
  }

  def createSchema(path: String): Try[Schema] = Try {
    val inputStream = environment.resourceAsStream(path).get
    val rawSchema   = new JSONObject(new JSONTokener(inputStream))
    SchemaLoader.load(rawSchema)
  }
}

object SchemaValidationService {

  final case class SchemaValidationError(key: String, message: String)
}
