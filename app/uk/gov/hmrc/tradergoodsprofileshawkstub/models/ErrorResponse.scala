package uk.gov.hmrc.tradergoodsprofileshawkstub.models

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.time.Instant

final case class ErrorResponse(
                                correlationId: String,
                                timestamp: Instant,
                                errorCode: String,
                                errorMessage: String,
                                source: String,
                                detail: Seq[String]
                              )

object ErrorResponse {

  implicit lazy val writes: OWrites[ErrorResponse] =
    (
      (__ \ "errorDetail" \ "correlationId").write[String] ~
      (__ \ "errorDetail" \ "timestamp").write[Instant] ~
      (__ \ "errorDetail" \ "errorCode").write[String] ~
      (__ \ "errorDetail" \ "errorMessage").write[String] ~
      (__ \ "errorDetail" \ "source").write[String] ~
      (__ \ "errorDetail" \ "sourceFaultDetail" \ "detail").write[Seq[String]]
    )(unlift(ErrorResponse.unapply))
}