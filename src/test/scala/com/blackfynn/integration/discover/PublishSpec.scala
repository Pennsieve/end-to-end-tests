package com.blackfynn.integration.discover

import cats.effect.IO
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.Tags._
import com.blackfynn.integration.UriCreator._
import com.blackfynn.integration.EndToEndConfig
import com.blackfynn.integration.BlackfynnEnvironment._
import com.blackfynn.integration.models.Dataset._
import com.blackfynn.integration.models.{ DatasetFixture, DatasetId }
import com.blackfynn.models.PublishStatus
import io.circe._
import org.http4s.Uri
import io.circe.generic.semiauto._
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.dsl.io._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{ Interval, Timeout }
import org.scalatest.{ Matchers, WordSpecLike }

import enumeratum.EnumEntry.Lowercase
import enumeratum.{ CirceEnum, Enum, EnumEntry }
import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

import scala.concurrent.duration._

class PublishSpec extends WordSpecLike with Matchers with DatasetFixture with Eventually {

  val config: EndToEndConfig = EndToEndConfig.loadOrThrow

  config.bfEnvironment match {
    case Development =>
      "A user should be able to publish a dataset" taggedAs (Slow, Discover) in withDataset {
        datasetId =>
          // Upload banner and readme, and then publish the dataset
          val io = for {
            _ <- IO { println(s"Setting up dataset $datasetId...") }

            _ <- client
              .successful(ioPutDatasetReadme(datasetId, "## A markdown readme"))
              .map(_ shouldBe true)

            _ <- IO { println(s"Created readme") }

            _ <- client
              .successful(ioPutDatasetBanner(datasetId))
              .map(_ shouldBe true)

            _ <- IO { println(s"Uploaded banner image") }

            _ <- client
              .successful(ioRequestDatasetPublication(datasetId))
              .map(_ shouldBe true)

            _ <- IO { println(s"Requested publication") }

            _ <- client
              .successful(ioAcceptDatasetPublication(datasetId))
              .map(_ shouldBe true)

            _ <- IO { println(s"Accepted publication") }

          } yield ()

          io.unsafeRunTimed(10.seconds).get

          // The dataset will not be visible until the publish job completes
          val publicDto: PublicDatasetDTO =
            eventually(Timeout(10.minutes), Interval(30.seconds)) {
              val io = for {
                _ <- IO { println(s"Polling publication status...") }
                publishStatus <- client
                  .expectCatch[PublishStatusResponse](ioGetPublicDatasetStatus(datasetId))
                dto <- client
                  .expectCatch[PublicDatasetDTO](ioGetPublicDataset(datasetId, publishStatus))
              } yield dto

              io.unsafeRunTimed(10.seconds).get
            }

          publicDto.status shouldBe PublishStatus.PublishSucceeded

          println("Publication succeeded")

          //check that the new fields version, description and Rights are present the DOI metadata

          println("Checking DOI attributes...")

          val doiResponse: Json =
            eventually(Timeout(1.minutes), Interval(5.seconds)) {
              client
                .expectCatch[Json](
                  GET(Uri.uri("https://api.test.datacite.org").withPath(s"/dois/${publicDto.doi}"))
                )
                .unsafeRunTimed(10.seconds)
                .get
            }

          println(s"Got DOI ${doiResponse.toString}")

          doiResponse.hcursor
            .downField("data")
            .downField("attributes")
            .downField("version")
            .as[Int]
            .right
            .get shouldBe 1

          doiResponse.hcursor
            .downField("data")
            .downField("attributes")
            .downField("descriptions")
            .as[List[Description]]
            .right
            .get shouldBe List(Description("An informative description"))

          doiResponse.hcursor
            .downField("data")
            .downField("attributes")
            .downField("rightsList")
            .as[List[Rights]]
            .right
            .get shouldBe List(Rights("MIT", "https://spdx.org/licenses/MIT.json"))

          val unpublishIo = for {
            _ <- IO { println(s"Unpublishing dataset...") }

            _ <- client
              .successful(ioRequestUnpublishDataset(datasetId))
              .map(_ shouldBe true)

            _ <- IO { println(s"Requested removal") }

            _ <- client
              .successful(ioAcceptUnpublishDataset(datasetId))
              .map(_ shouldBe true)

            _ <- IO { println(s"Accepted removal") }
          } yield ()

          unpublishIo.unsafeRunTimed(10.seconds).get
      }
    case Production => Nil
  }

  def ioGetPublicDataset(
    datasetId: DatasetId,
    publishStatus: PublishStatusResponse
  ): IO[Request[IO]] =
    GET(
      toUri(
        s"/discover/datasets/${publishStatus.publishedDatasetId}/versions/${publishStatus.publishedVersionCount}"
      )
    )
}

case class PublishStatusResponse(publishedDatasetId: Int, publishedVersionCount: Int)

object PublishStatusResponse {
  implicit val encoder: Encoder[PublishStatusResponse] = deriveEncoder[PublishStatusResponse]
  implicit val decoder: Decoder[PublishStatusResponse] = deriveDecoder[PublishStatusResponse]
}

case class PublicDatasetDTO(id: Int, version: Int, doi: String, status: PublishStatus)

object PublicDatasetDTO {
  implicit val encoder: Encoder[PublicDatasetDTO] = deriveEncoder[PublicDatasetDTO]
  implicit val decoder: Decoder[PublicDatasetDTO] = deriveDecoder[PublicDatasetDTO]
}

case class Description(description: String, descriptionType: String)
object Description {
  implicit val decoder: Decoder[Description] = deriveDecoder
  implicit val encoder: Encoder[Description] = deriveEncoder

  def apply(description: String): Description = {
    Description(description, "Abstract")
  }
}

case class Rights(rights: String, rightsUri: String)
object Rights {
  implicit val decoder: Decoder[Rights] = deriveDecoder
  implicit val encoder: Encoder[Rights] = deriveEncoder
}
