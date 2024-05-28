package uk.gov.hmrc.tradergoodsprofileshawkstub.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.CREATED
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models._
import uk.gov.hmrc.tradergoodsprofileshawkstub.models.requests.CreateGoodsItemRecordRequest
import uk.gov.hmrc.tradergoodsprofileshawkstub.repositories.GoodsItemRecordRepository
import uk.gov.hmrc.tradergoodsprofileshawkstub.services.UuidService

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future

class GoodsItemRecordsControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues {

  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val mockRepository = mock[GoodsItemRecordRepository]
  private val mockUuidService = mock[UuidService]

  private val rfc7231Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private val formattedDate = clock.instant().atZone(ZoneId.of("GMT")).format(rfc7231Formatter)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "expected-auth-header" -> "some-token"
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[GoodsItemRecordRepository].toInstance(mockRepository),
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRepository)
  }


  "createRecord" - {

    val correlationId = UUID.randomUUID().toString
    val forwardedHost = "forwarded-for"
    val record = generateRecord

    val requestBody = CreateGoodsItemRecordRequest(
      eori = record.goodsItem.eori,
      actorId = record.goodsItem.actorId,
      traderRef = record.goodsItem.traderRef,
      comcode = record.goodsItem.comcode,
      goodsDescription = record.goodsItem.goodsDescription,
      countryOfOrigin = record.goodsItem.countryOfOrigin,
      category = record.goodsItem.category,
      assessments = Some(record.goodsItem.assessments),
      supplementaryUnit = record.goodsItem.supplementaryUnit,
      measurementUnit = record.goodsItem.measurementUnit,
      comcodeEffectiveFromDate = record.goodsItem.comcodeEffectiveFromDate,
      comcodeEffectiveToDate = record.goodsItem.comcodeEffectiveToDate
    )

    "must create a record and return the relevant response when given a valid request" in {

      val request = FakeRequest(routes.RecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))

      val result = route(app, request).value

      status(result) mustEqual CREATED

      contentAsJson(result) mustEqual record.toCreateRecordResponse
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository).insert(requestBody)
    }

    "must not create a record and return an error when there is no correlation-id header" in {

      val request = FakeRequest(routes.RecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 001, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no forwarded-host header" in {

      val request = FakeRequest(routes.RecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 005, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result) mustBe empty
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no date header" in {

      val request = FakeRequest(routes.RecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 002, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is an invalid date header" in {

      val request = FakeRequest(routes.RecordsController.createRecord()).withBody(Json.toJson(requestBody))
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "application/json",
          "Date" -> "invalid",
          "Accept" -> "application/json",
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 002, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no content-type header" in {

      val request = FakeRequest(routes.RecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is an invalid content-type header" in {

      val request = FakeRequest(routes.RecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.toJson(expectedResponse)
      header("X-Correlation-ID", result).value mustEqual correlationId
      header("X-Forwarded-Host", result).value mustEqual forwardedHost
      header("Content-Type", result).value mustEqual "application/json"

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return an error when there is no profile matching the eori" ignore {

    }

    "must not create a record and return forbidden with no body when there is no authorization header" in {

      val request = FakeRequest(routes.RecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).insert(any)
    }

    "must not create a record and return forbidden with no body when there is an invalid authorization header" in {

      val request = FakeRequest(routes.RecordsController.createRecord())
        .withHeaders(
          "X-Correlation-ID" -> correlationId,
          "X-Forwarded-Host" -> forwardedHost,
          "Content-Type" -> "text/xml",
          "Accept" -> "application/json",
          "Date" -> formattedDate,
          "Authorization" -> "some-other-token"
        )

      when(mockRepository.insert(any)).thenReturn(Future.successful(record))
      when(mockUuidService.generate()).thenReturn(correlationId)

      val expectedResponse = ErrorResponse(
        correlationId = correlationId,
        timestamp = clock.instant(),
        errorCode = "400",
        errorMessage = "Bad Request",
        source = "BACKEND",
        detail = Seq(
          "error: 003, message: Invalid Header"
        )
      )

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN

      verify(mockRepository, never).insert(any)
    }
  }

  private def generateRecord = GoodsItemRecord(
    recordId = UUID.randomUUID().toString,
    goodsItem = GoodsItem(
      eori = "eori",
      actorId = "actorId",
      traderRef = "traderRef",
      comcode = "comcode",
      goodsDescription = "goodsDescription",
      countryOfOrigin = "countryOfOrigin",
      category = 1,
      assessments = Seq(
        Assessment(
          assessmentId = Some("assessmentId"),
          primaryCategory = Some(2),
          condition = Some(Condition(
            `type` = Some("type"),
            conditionId = Some("conditionId"),
            conditionDescription = Some("conditionDescription"),
            conditionTraderText = Some("conditionTraderText")
          ))
        )
      ),
      supplementaryUnit = Some(BigDecimal(2.5)),
      measurementUnit = Some("measurementUnit"),
      comcodeEffectiveFromDate = clock.instant().minus(1, ChronoUnit.DAYS),
      comcodeEffectiveToDate = Some(clock.instant().plus(1, ChronoUnit.DAYS))
    ),
    metadata = GoodsItemMetadata(
      accreditationStatus = AccreditationStatus.NotRequested,
      version = 1,
      active = true,
      locked = false,
      toReview = false,
      reviewReason = None,
      declarable = "declarable",
      ukimsNumber = None,
      nirmsNumber = None,
      niphlNumber = None,
      srcSystemName = "MDTP",
      updatedDateTime = clock.instant(),
      createdDateTime = clock.instant().minus(1, ChronoUnit.HOURS)
    )
  )
}