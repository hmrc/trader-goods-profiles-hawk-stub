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

package uk.gov.hmrc.tradergoodsprofileshawkstub.controllers

import cats.data.EitherT
import cats.syntax.all._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents, RawBuffer, Request, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.ValidationRules.ValidatedHeaders
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.responses.TraderProfileResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneId}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GetProfileController @Inject() (
  override val controllerComponents: ControllerComponents,
  override val uuidService: UuidService,
  override val clock: Clock,
  override val configuration: Configuration,
  override val schemaValidationService: SchemaValidationService,
  override val traderProfilesRepository: TraderProfileRepository
)(implicit val ec: ExecutionContext)
    extends BackendBaseController
    with ValidationRules {

  def getProfile(eori: String): Action[RawBuffer] = Action.async(parse.raw) { implicit request =>
    (for {
      _       <- EitherT.fromEither[Future](validateAuthorization)
      _       <- EitherT.fromEither[Future](validateHeaders)
      profile <- getTraderProfile(eori)
    } yield Ok(Json.toJson(TraderProfileResponse.createFrom(profile)))).merge
  }

  private def validateHeaders(implicit request: Request[_]): Either[Result, ValidatedHeaders] =
    (
      validateCorrelationId,
      validateForwardedHost,
      validateDate,
      validateAccept
    ).parMapN { (correlationId, forwardedHost, _, _) =>
      ValidatedHeaders(correlationId, forwardedHost)
    } leftMap { errors =>
      val correlationId = request.headers.get("X-Correlation-Id").getOrElse(uuidService.generate())

      val headers = Seq(
        Some("X-Correlation-Id" -> correlationId),
        Some("Date"             -> getDateAsRFC7231Format),
        Some("Content-Type"     -> "application/json")
      ).flatten

      BadRequest(
        Json.toJson(
          ErrorResponse(
            correlationId = correlationId,
            timestamp = clock.instant(),
            errorCode = "400",
            errorMessage = "Bad Request",
            source = "BACKEND",
            detail = errors.toList
          )
        )
      ).withHeaders(headers: _*)
    }

  private def getDateAsRFC7231Format = {
    val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
    clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)
  }
}
