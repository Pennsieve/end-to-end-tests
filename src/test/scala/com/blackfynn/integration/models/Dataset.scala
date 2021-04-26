package com.blackfynn.integration.models

import java.util.UUID

import cats.effect.{ ContextShift, IO }
import cats.implicits._
import com.blackfynn.integration.BlackfynnEnvironment
import com.blackfynn.integration.IOClient.{ client, RichClient }
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.models.Login._
import com.blackfynn.models.{ License, PackagesPage }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.parser._
import io.circe.syntax.EncoderOps
import io.circe.{ Decoder, Encoder, HCursor }
import org.http4s.Method.{ DELETE, GET, POST, PUT }
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe._
import org.http4s.client.dsl.io._
import org.http4s.Request
import org.http4s.Header
import org.http4s.{ MediaType, Request }
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{ Multipart, Part }
import org.scalatest.Matchers
import com.blackfynn.integration.trials.{ config, _ }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Dataset {

  val ioAllDatasets: IO[Request[IO]] = GET(toUri("/datasets/"), bearerTokenHeader, sessionHeader)

  def ioGetDataset(datasetId: DatasetId): IO[Request[IO]] =
    GET(toUri(s"/datasets/${datasetId.value}"), bearerTokenHeader, sessionHeader)

  def ioGetDatasetForTrial(datasetId: DatasetId): IO[Request[IO]] =
    GET(toUri(s"/datasets/${datasetId.value}"), trialUserTokenHeader, trialUserSessionHeader)

  def ioCreateDataset(
    name: String,
    bearerTokenHeader: Header,
    sessionHeader: Header,
    automaticallyProcessPackages: Boolean = false
  ): IO[Request[IO]] =
    POST(
      CreateDatasetRequest(name, automaticallyProcessPackages = automaticallyProcessPackages).asJson,
      toUri(s"/datasets/"),
      bearerTokenHeader,
      sessionHeader
    )

  def ioDeleteDataset(
    datasetId: DatasetId,
    bearerTokenHeader: Header,
    sessionHeader: Header
  ): IO[Request[IO]] =
    DELETE(toUri(s"/datasets/${datasetId.value}"), bearerTokenHeader, sessionHeader)

  def ioPutDatasetReadme(datasetId: DatasetId, readme: String): IO[Request[IO]] =
    PUT(
      UpdateDatasetReadmeRequest(readme).asJson,
      toUri(s"/datasets/${datasetId.value}/readme"),
      bearerTokenHeader,
      sessionHeader
    )

  def ioPutDatasetBanner(
    datasetId: DatasetId
  )(implicit
    contextShift: ContextShift[IO]
  ): IO[Request[IO]] = {
    for {
      banner <- IO(createFile("discover/banner.jpg"))
      body <- IO(
        Multipart[IO](
          Vector(Part.fileData("banner", banner, global, `Content-Type`(MediaType.image.jpeg)))
        )
      )
      request <- PUT(
        body,
        toUri(s"/datasets/${datasetId.value}/banner"),
        bearerTokenHeader,
        sessionHeader
      )
    } yield request.putHeaders(body.headers.toList: _*)
  }

  def ioRequestDatasetPublication(datasetId: DatasetId): IO[Request[IO]] =
    POST(
      toUri(s"/datasets/${datasetId.value}/publication/request?publicationType=publication"),
      bearerTokenHeader,
      sessionHeader
    )

  def ioAcceptDatasetPublication(datasetId: DatasetId): IO[Request[IO]] =
    POST(
      toUri(s"/datasets/${datasetId.value}/publication/accept?publicationType=publication"),
      bearerTokenHeader,
      sessionHeader
    )

  def ioRequestUnpublishDataset(datasetId: DatasetId): IO[Request[IO]] =
    POST(
      toUri(s"/datasets/${datasetId.value}/publication/request?publicationType=removal"),
      bearerTokenHeader,
      sessionHeader
    )

  def ioAcceptUnpublishDataset(datasetId: DatasetId): IO[Request[IO]] =
    POST(
      toUri(s"/datasets/${datasetId.value}/publication/accept?publicationType=removal"),
      bearerTokenHeader,
      sessionHeader
    )

  def ioGetPublicDatasetStatus(datasetId: DatasetId): IO[Request[IO]] =
    GET(toUri(s"/datasets/${datasetId.value}/published"), bearerTokenHeader, sessionHeader)

  def ioDatasetPackages(
    datasetId: DatasetId,
    maybeCursor: Option[String] = None
  ): IO[Request[IO]] = {
    val cursorParam = maybeCursor.fold("")(cursor => s"?cursor=$cursor")
    GET(
      toUri(s"/datasets/${datasetId.value}/packages$cursorParam"),
      bearerTokenHeader,
      sessionHeader
    )
  }

  def ioDatasetPackagesForTrial(
    datasetId: DatasetId,
    maybeCursor: Option[String] = None
  ): IO[Request[IO]] = {
    val cursorParam = maybeCursor.fold("")(cursor => s"?cursor=$cursor")
    GET(
      toUri(s"/datasets/${datasetId.value}/packages$cursorParam"),
      trialUserTokenHeader,
      trialUserSessionHeader
    )
  }

  def ioRecursePackagesOnDataset(datasetId: DatasetId) =
    client
      .expect[PackagesPage](ioDatasetPackages(datasetId))
      .tailRecM { currentPage =>
        currentPage.map {
          case PackagesPage(packages, Some(cursor)) =>
            client
              .expect[PackagesPage](ioDatasetPackages(datasetId, Some(cursor)))
              .map(nextPage => nextPage.copy(nextPage.packages ++ packages))
              .asLeft

          case PackagesPage(packages, None) => packages.asRight
        }
      }

  implicit val pageDecoder: Decoder[PackagesPage] = deriveDecoder[PackagesPage]

}

case class DatasetId(value: String)

object DatasetId {
  implicit val decoder: Decoder[DatasetId] =
    (cursor: HCursor) =>
      cursor
        .downField("content")
        .downField("id")
        .as[String]
        .map(apply)
}

case class CreateDatasetRequest(
  name: String,
  description: String = "An informative description",
  license: License = License.MIT,
  contributors: List[String] = List("Mr. Maker"),
  tags: List[String] = List("test-tag"),
  automaticallyProcessPackages: Boolean
)

object CreateDatasetRequest {
  implicit val encoder: Encoder[CreateDatasetRequest] = deriveEncoder[CreateDatasetRequest]
}

case class UpdateDatasetReadmeRequest(readme: String)

object UpdateDatasetReadmeRequest {
  implicit val encoder: Encoder[UpdateDatasetReadmeRequest] =
    deriveEncoder[UpdateDatasetReadmeRequest]
}

/**
  * Scalatest fixture that creates a new dataset at the beginning of the test
  * and deletes it afterwards.
  */
trait DatasetFixture {
  this: Matchers =>

  val fixtureSessionToken: String = sessionToken

  val fixtureSessionHeader: Header = sessionHeader

  val fixtureOrganization: String = organization

  val fixtureBearerTokenHeader: Header = bearerTokenHeader

  def withDataset(testCode: DatasetId => Any): Any = withDataset()(testCode)

  def withDataset(timeout: FiniteDuration = 10 minutes)(testCode: DatasetId => Any): Any = {

    val name = s"End-to-end test dataset ${UUID.randomUUID()}"

    val createDataset: IO[DatasetId] = client
      .expectCatch[DatasetId](
        Dataset.ioCreateDataset(name, fixtureBearerTokenHeader, fixtureSessionHeader, false)
      )

    def deleteDataset(datasetId: DatasetId): IO[Unit] =
      client
        .expectCatch[Unit](
          Dataset.ioDeleteDataset(datasetId, fixtureBearerTokenHeader, fixtureSessionHeader)
        )
        .map(_ => ())

    createDataset
      .bracket(datasetId => IO(testCode(datasetId)))(deleteDataset)
      .unsafeRunTimed(timeout)
      .get

  }
}

/**
  * Scalatest fixture that creates a new trial dataset at the beginning of the test
  * and deletes it afterwards.
  */
trait TrialDatasetFixture extends DatasetFixture {
  this: Matchers =>

  override val fixtureSessionToken: String = trialUserSessionToken

  override val fixtureSessionHeader: Header = trialUserSessionHeader

  override val fixtureOrganization: String = trialUserOrganization

  override val fixtureBearerTokenHeader: Header = trialUserTokenHeader

  def withTrialDataset(timeout: FiniteDuration = 25 minutes)(testCode: DatasetId => Any) = {

    if (config.bfEnvironment == BlackfynnEnvironment.Development) {

      val name = s"End-to-end test dataset ${UUID.randomUUID()}"

      val createDataset: IO[DatasetId] = client
        .expectCatch[DatasetId](
          Dataset.ioCreateDataset(name, fixtureBearerTokenHeader, fixtureSessionHeader)
        )

      def deleteDataset(datasetId: DatasetId): IO[Unit] =
        client
          .expectCatch[Unit](
            Dataset.ioDeleteDataset(datasetId, fixtureBearerTokenHeader, fixtureSessionHeader)
          )
          .map(_ => ())

      def setBlindReviewerRole(datasetId: DatasetId): IO[DatasetId] =
        client
          .successful(
            PUT(
              parse(
                s"""{ "id": "${blindReviewerLoginResponse.profile.id}", "role": "blind_reviewer" }""".stripMargin
              ).right.get,
              toUri(
                s"/datasets/${datasetId.value}/collaborators/users?api_key=$trialUserSessionToken"
              ),
              trialUserTokenHeader,
              trialUserSessionHeader
            )
          )
          .map(_ shouldBe true)
          .map(_ => datasetId)

      val io = for {
        datasetId <- createDataset
        _ <- setBlindReviewerRole(datasetId)
          .bracket(datasetId => IO(testCode(datasetId)))(deleteDataset)
      } yield ()

      io.unsafeRunTimed(timeout).get
    }
  }

}
