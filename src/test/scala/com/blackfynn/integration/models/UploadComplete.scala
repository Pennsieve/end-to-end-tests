package com.blackfynn.integration.models

import cats.effect.IO
import com.blackfynn.dtos.PackageDTO
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.models.Login._
import com.blackfynn.models.Manifest
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto._
import org.http4s.Method.POST
import org.http4s.{ circe, EntityDecoder, Header, Headers, Request }
import com.blackfynn.integration.trials._

object UploadComplete {

  def uploadCompleteRequest(
    importId: String,
    datasetId: String,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader,
    sessionHeader: Header = sessionHeader
  ): Request[IO] = {
    Request[IO](
      POST,
      toUri(s"/upload/complete/organizations/$organization/id/$importId?datasetId=$datasetId"),
      headers = Headers.of(tokenHeader, sessionHeader)
    )
  }
}

case class UploadCompleteResponse(manifest: Manifest, `package`: Option[PackageDTO])

object UploadCompleteResponse {
  implicit val decoder: Decoder[UploadCompleteResponse] = deriveDecoder[UploadCompleteResponse]
  val entityDecoder: EntityDecoder[IO, List[UploadCompleteResponse]] =
    circe.jsonOf[IO, List[UploadCompleteResponse]]
}

final class FileMissingParts(
  val fileName: String,
  val missingParts: List[Int],
  val expectedTotalParts: Int
)

final case class FilesMissingParts(files: List[FileMissingParts])

object FilesMissingParts {
  implicit val decoderFileMissingParts: Decoder[FileMissingParts] = deriveDecoder[FileMissingParts]
  implicit val encoderFileMissingParts: Encoder[FileMissingParts] = deriveEncoder[FileMissingParts]

  implicit val decoder: Decoder[FilesMissingParts] = deriveDecoder[FilesMissingParts]
  implicit val encoder: Encoder[FilesMissingParts] = deriveEncoder[FilesMissingParts]

  val entityDecoder: EntityDecoder[IO, FilesMissingParts] = circe.jsonOf[IO, FilesMissingParts]
}
