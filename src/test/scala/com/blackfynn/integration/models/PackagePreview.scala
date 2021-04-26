package com.blackfynn.integration.models

import java.io.File

import cats.effect.IO
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.models.{ FileType, PackageType }
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.syntax.EncoderOps
import org.http4s.{ EntityDecoder, Header, Request }
import org.http4s.client.dsl.io._
import org.http4s.circe._
import org.http4s.Method.POST
import org.http4s.circe.jsonOf
import com.blackfynn.integration.models.Login.{ bearerTokenHeader, organization, sessionToken }
import com.blackfynn.integration.trials._

case class PreviewPackageRequest(files: List[S3File])

object PreviewPackageRequest {
  implicit val encoderChunkedUpload: Encoder[ChunkedUpload] = deriveEncoder[ChunkedUpload]
  implicit val encoderS3File: Encoder[S3File] = deriveEncoder[S3File]
  implicit val encodePreviewRequest: Encoder[PreviewPackageRequest] =
    deriveEncoder[PreviewPackageRequest]
}

case class PreviewPackageResponse(packages: List[PackagePreview])

object PreviewPackageResponse {
  implicit val decoderChunkedUpload: Decoder[ChunkedUpload] = deriveDecoder[ChunkedUpload]
  implicit val s3Decoder: Decoder[S3File] = deriveDecoder[S3File]
  implicit val decodePreview: Decoder[PackagePreview] = deriveDecoder[PackagePreview]
  implicit val decodePreviewResponse: Decoder[PreviewPackageResponse] =
    deriveDecoder[PreviewPackageResponse]
  implicit val decoder: EntityDecoder[IO, PreviewPackageResponse] =
    jsonOf[IO, PreviewPackageResponse]
}

case class PackagePreview(
  packageName: String,
  packageType: PackageType,
  packageSubtype: String,
  fileType: FileType,
  files: List[S3File],
  warnings: Iterable[String],
  groupSize: Long,
  hasWorkflow: Boolean,
  importId: String,
  icon: String
)

object PackagePreview {

  val smallCSVName: String = "end-to-end-test-simple.csv"
  val smallCSV: File = createFile(smallCSVName)

  val largeZipName: String = "large.zip"
  val largeZip: File = createFile(largeZipName)

  val testGeneratorName: String = "test_generator.edf"
  val testGenerator: File = createFile(testGeneratorName)

  val catFileName: String = "cat.jpg"
  val catFile: File = createFile(catFileName)

  val montagingDataName: String = "Montaging_data.edf"
  val montagingData: File = createFile(montagingDataName)

  val dicomDirectory: String = "dicom"
  val dicomFiles: List[File] = createFile(dicomDirectory).listFiles.toList

  def previewPackageRequest(file: File): PreviewPackageRequest =
    PreviewPackageRequest(List(S3File(Some(1), file.getName, file.length)))

  def collectionPreviewRequest(files: List[File]) =
    PreviewPackageRequest {
      files.zipWithIndex
        .map {
          case (file, index) =>
            S3File(
              Some(index),
              file.getName,
              file.length(),
              filePath = Some(file.getParentFile.getPath)
            )
        }
    }

  def ioPreviewRequest(file: File): IO[Request[IO]] =
    POST(
      previewPackageRequest(file).asJson,
      toUri("/files/upload/preview?append=false"),
      bearerTokenHeader,
      Header("X-SESSION-ID", sessionToken)
    )

  def ioNewPreviewRequest(
    file: File,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader
  ): IO[Request[IO]] = {
    POST(
      previewPackageRequest(file).asJson,
      toUri(s"/upload/preview/organizations/$organization?append=false&datasetId=doesntmatter"),
      tokenHeader
    )
  }

  def ioNewPreviewRequest(previewRequest: PreviewPackageRequest): IO[Request[IO]] =
    POST(
      previewRequest.asJson,
      toUri(s"/upload/preview/organizations/$organization?append=false&datasetId=doesntmatter"),
      bearerTokenHeader
    )
}

case class ChunkedUpload(chunkSize: Long, totalChunks: Int)

case class S3File(
  uploadId: Option[Int],
  fileName: String,
  size: Long,
  multipartUploadId: Option[String] = None,
  chunkedUpload: Option[ChunkedUpload] = None,
  filePath: Option[String] = None
)
