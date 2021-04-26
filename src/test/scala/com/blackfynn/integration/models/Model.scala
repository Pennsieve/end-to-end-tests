package com.blackfynn.integration.models

import java.util.UUID

import cats.effect.{ ContextShift, IO }
import cats.implicits._
import com.blackfynn.integration.IOClient.{ client, RichClient }
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.models.Login._
import io.circe._
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.syntax._
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
import com.blackfynn.integration.trials._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class CreateModelRequest(name: String, displayName: String, description: String)

object CreateModelRequest {
  implicit val encoder: Encoder[CreateModelRequest] = deriveEncoder[CreateModelRequest]
  implicit val decoder: Decoder[CreateModelRequest] = deriveDecoder[CreateModelRequest]
}

object Model {

  def ioCreateModel(datasetId: DatasetId, name: String): IO[Request[IO]] =
    POST(
      CreateModelRequest(name, name, name).asJson,
      toUri(s"/models/datasets/${datasetId.value}/concepts"),
      bearerTokenHeader,
      sessionHeader
    )
}
