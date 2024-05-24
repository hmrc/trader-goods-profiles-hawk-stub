package uk.gov.hmrc.tradergoodsprofileshawkstub.models

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

import java.time.{Clock, Instant, ZoneOffset}

class ErrorResponseSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

  "must write to correct json" in {

    val model = ErrorResponse(
      correlationId = "correlationId",
      timestamp = clock.instant(),
      errorCode = "errorCode",
      errorMessage = "errorMessage",
      source = "source",
      detail = Seq("error1", "error2")
    )

    val expectedJson = Json.obj(
      "errorDetail" -> Json.obj(
        "correlationId" -> "correlationId",
        "timestamp" -> clock.instant(),
        "errorCode" -> "errorCode",
        "errorMessage" -> "errorMessage",
        "source" -> "source",
        "sourceFaultDetail" -> Json.obj(
          "detail" -> Json.arr("error1", "error2")
        )
      )
    )

    Json.toJson(model) mustEqual expectedJson
  }
}
