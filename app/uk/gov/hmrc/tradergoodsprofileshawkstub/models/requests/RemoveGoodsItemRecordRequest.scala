package uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests

import play.api.libs.json.{Json, OFormat}

final case class RemoveGoodsItemRecordRequest(
                                               eori: String,
                                               recordId: String,
                                               actorId: String
                                             )

object RemoveGoodsItemRecordRequest {

  implicit lazy val format: OFormat[RemoveGoodsItemRecordRequest] = Json.format
}
