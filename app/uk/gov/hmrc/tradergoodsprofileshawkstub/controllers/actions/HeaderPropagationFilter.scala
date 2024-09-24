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

package uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.actions

import play.api.mvc.{ActionFunction, Request, Result}
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HeaderPropagationFilter @Inject() (
  uuidService: UuidService
)(implicit override val executionContext: ExecutionContext)
    extends ActionFunction[Request, Request] {

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
    block(request).map { result =>
      val correlationId = request.headers
        .get("X-Correlation-Id")
        .orElse(result.header.headers.get("X-Correlation-Id"))
        .getOrElse(uuidService.generate())

      val forwardedHost = request.headers.get("X-Forwarded-Host")

      val headers = Seq(
        Some("X-Correlation-Id"              -> correlationId),
        forwardedHost.map("X-Forwarded-Host" -> _),
        Some("Content-Type"                  -> "application/json")
      ).flatten

      result.withHeaders(headers: _*)
    }
}
