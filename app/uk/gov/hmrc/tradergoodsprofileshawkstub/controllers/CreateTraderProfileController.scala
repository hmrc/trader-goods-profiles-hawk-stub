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
import cats.implicits.{catsStdInstancesForFuture, catsSyntaxTuple4Parallel, toFoldableOps}
import cats.syntax.all._
import org.apache.pekko.Done
import org.everit.json.schema.Schema
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.tradergoodsprofileshawkstub.controllers.ValidationRules.ValidatedHeaders
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.ErrorResponse
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateTraderProfileRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.TraderProfileRepository.DuplicateEoriException
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.{SchemaValidationService, UuidService}

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class CreateTraderProfileController @Inject() (
  override val controllerComponents: ControllerComponents,
  override val traderProfilesRepository: TraderProfileRepository,
  override val uuidService: UuidService,
  override val clock: Clock,
  override val configuration: Configuration,
  override val schemaValidationService: SchemaValidationService
)(implicit override val ec: ExecutionContext)
    extends BackendBaseController
    with ValidationRules {

  private val createProfileSchema: Schema =
    schemaValidationService.createSchema("/schemas/tgp-create-profile-request-v0.1.json").get
  private val rfc7231Formatter            = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")

  def createProfile(): Action[RawBuffer] = Action.async(parse.raw) { implicit request =>
    val result = for {
      _                <- EitherT.fromEither[Future](validateAuthorization)
      validatedHeaders <- EitherT.fromEither[Future](validatePostHeaders)
      body             <- EitherT.fromEither[Future](validateRequestBody[CreateTraderProfileRequest](createProfileSchema))
      _                <- createTraderProfile(body)
    } yield Created
      .withHeaders(
        "X-Correlation-ID" -> validatedHeaders.correlationId,
        "Date"             -> clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)
      )

    result.merge
  }

  private def createTraderProfile(body: CreateTraderProfileRequest)(implicit
    request: Request[_],
    ec: ExecutionContext
  ): EitherT[Future, Result, Done] =
    EitherT(
      traderProfilesRepository.insert(body).transform {
        case Success(done) =>
          Success(Right(done))
        case Failure(e)    =>
          e match {
            case DuplicateEoriException =>
              Success(
                Left(
                  badRequest(
                    errorCode = "400",
                    errorMessage = "Bad Request",
                    source = "BACKEND",
                    detail = Seq(
                      "error: 038, message: Invalid Request Parameter"
                    )
                  )
                )
              )
            case _                      =>
              Success(
                Left(
                  badRequest(
                    errorCode = "400",
                    errorMessage = "Bad Request",
                    source = "BACKEND",
                    detail = Seq(
                      "error: 007, message: Invalid Request Parameter"
                    )
                  )
                )
              )
          }
      }
    )

  private def validatePostHeaders(implicit request: Request[_]): Either[Result, ValidatedHeaders] =
    (
      validateCorrelationId,
      validateForwardedHost,
      validateDate,
      validateContentType
    ).parMapN { (correlationId, forwardedHost, _, _) =>
      ValidatedHeaders(correlationId, forwardedHost)
    }.leftMap { errors =>
      val correlationId = request.headers.get("X-Correlation-Id").getOrElse(uuidService.generate())
      val forwardedHost = request.headers.get("X-Forwarded-Host")

      val headers = Seq(
        Some("X-Correlation-Id"              -> correlationId),
        forwardedHost.map("X-Forwarded-Host" -> _),
        Some("Content-Type"                  -> "application/json")
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
}
