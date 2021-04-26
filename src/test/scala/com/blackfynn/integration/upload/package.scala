package com.blackfynn.integration

import java.io.File
import java.util.concurrent.Executors
import java.security.MessageDigest
import java.time.{ ZoneOffset, ZonedDateTime }

import cats.effect.{ ContextShift, IO }
import cats.implicits._
import com.blackfynn.integration.IOClient.{ client, RichClient }
import com.blackfynn.integration.models._
import com.blackfynn.integration.models.Dataset._
import com.blackfynn.integration.models.PackagePreview._
import com.blackfynn.integration.models.Login._
import com.blackfynn.integration.models.UploadComplete.uploadCompleteRequest

import com.blackfynn.integration.UriCreator.toUri
import fs2.{ Chunk, Stream }
import fs2.io.file.readAll
import fs2.text.utf8Decode
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.Status._
import org.http4s.circe._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.headers.`Content-Disposition`
import org.http4s.multipart._
import org.http4s.{ Header, Response }
import org.http4s.{ EntityDecoder, EntityEncoder, Headers, Request }
import org.http4s.Method.POST

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

package object upload {
  type CreateChunkRequest =
    (String, String, Chunk[Byte], Long, String, String, Header) => Request[IO]

  case class UploadResponse(success: Boolean, error: Option[String])

  object UploadResponse {
    implicit val decodeResponse: Decoder[UploadResponse] = deriveDecoder[UploadResponse]
    implicit val decoder: EntityDecoder[IO, UploadResponse] = jsonOf[IO, UploadResponse]
  }

  def blockingContextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)))

  def agentChunkRequest(
    multipartId: String,
    importId: String,
    chunk: Chunk[Byte],
    number: Long,
    fileName: String,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader
  ) = {
    val byteBuffer = chunk.toByteBuffer
    val digest = MessageDigest.getInstance("SHA-256").digest(byteBuffer.array())
    val hex = encodeHex(digest)

    Request[IO](
      POST,
      toUri(
        s"/upload/chunk/organizations/$organization/id/$importId?filename=$fileName&chunkNumber=$number&multipartId=$multipartId&chunkChecksum=$hex"
      ),
      body = Stream.chunk(chunk),
      headers = Headers.of(tokenHeader)
    )
  }

  def sendUploadRequest(request: Request[IO]): IO[UploadResponse] =
    client.expectCatch[UploadResponse](request)

  private val Digits = "0123456789abcdef".toCharArray

  def encodeHex(bytes: Array[Byte]): String = {
    val length = bytes.length
    val out = new Array[Char](length * 2)
    for (i <- 0 until length) {
      val b = bytes(i)
      out(i * 2) = Digits((b >> 4) & 0xF)
      out(i * 2 + 1) = Digits(b & 0xF)
    }
    new String(out)
  }

  @tailrec
  def recurseAndFlattenFileTree(
    toCheck: List[File],
    acc: List[File] = List.empty[File]
  ): List[File] =
    toCheck match {
      case Nil => acc
      case head :: tail if head.isDirectory =>
        recurseAndFlattenFileTree(tail, head.listFiles().toList ++ acc)
      case head :: tail =>
        recurseAndFlattenFileTree(tail, head :: acc)
    }

  def sendCompleteRequest(completeRequest: Request[IO]) =
    client
      .fetch(completeRequest) {
        case Successful(resp) =>
          UploadCompleteResponse.entityDecoder
            .decode(resp, strict = false)
            .map(_.asRight[FilesMissingParts])
            .value

        case resp if resp.status == NotFound =>
          FilesMissingParts.entityDecoder
            .decode(resp, strict = false)
            .map(_.asLeft[List[UploadCompleteResponse]])
            .value

        case Response(status, _, _, body, _) =>
          body
            .through(utf8Decode[IO])
            .compile
            .foldMonoid
            .map(body => new Exception(s"Returned $status $body").asLeft)
      }

  def putAllChunks(
    multipartId: String,
    importId: String,
    chunkSize: Long,
    file: File,
    createChunkRequest: CreateChunkRequest = fineUploaderChunkRequest,
    nonZero: Boolean = true,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader
  )(implicit
    contextShift: ContextShift[IO] = blockingContextShift
  ): IO[Either[Throwable, List[UploadResponse]]] =
    if (nonZero)
      readAll[IO](file.toPath, global, chunkSize.toInt).chunks.zipWithIndex
        .evalMap {
          case (chunk, number) =>
            val chunkRequest =
              createChunkRequest(
                multipartId,
                importId,
                chunk,
                number,
                file.getName,
                organization,
                tokenHeader
              )

            sendUploadRequest(chunkRequest)
              .map(_.asRight[Throwable])
              .recover {
                case e => e.asLeft
              }
        }
        .compile
        .toList
        .map {
          _.foldRight(List.empty[UploadResponse].asRight[Throwable]) {
            case (_, Left(e)) => e.asLeft
            case (Left(e), _) => e.asLeft
            case (Right(resp), acc) => acc.map(_ :+ resp)
          }
        } else
      sendUploadRequest(
        agentChunkRequest(
          multipartId,
          importId,
          Chunk.empty,
          0,
          file.getName,
          organization,
          tokenHeader
        )
      ).map(response => List(response).asRight[Throwable])
        .recover {
          case e => e.asLeft
        }

  private def fineUploaderChunkRequest(
    multipartId: String,
    importId: String,
    chunk: Chunk[Byte],
    number: Long,
    fileName: String,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader
  ) = {
    val multipart =
      Multipart[IO](
        Vector(
          Part.formData("qqfilename", fileName),
          Part.formData("qqchunksize", chunk.size.toString),
          Part.formData("qqpartindex", number.toString),
          Part(
            Headers.of(
              `Content-Disposition`("form-data", Map("name" -> "qqfile")),
              Header("Content-Transfer-Encoding", "binary")
            ),
            Stream.chunk(chunk)
          )
        )
      )

    val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)

    Request[IO](
      POST,
      toUri(
        s"/upload/fineuploaderchunk/organizations/$organization/id/$importId?multipartId=$multipartId"
      ),
      body = entity.body,
      headers = multipart.headers put tokenHeader
    )
  }

  def ioChunkUpload(
    file: File,
    datasetId: DatasetId,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader,
    sessionHeader: Header = sessionHeader
  )(implicit
    contextShift: ContextShift[IO] = blockingContextShift
  ) = {

    for {
      previewResponse <- {
        client.expectCatch[PreviewPackageResponse](
          ioNewPreviewRequest(file, organization, tokenHeader)
        )
      }

      importId = previewResponse.packages.head.importId

      packageToUpload = previewResponse.packages.head

      multipartId = packageToUpload.files.head.multipartUploadId.get

      chunkSize = packageToUpload.files.head.chunkedUpload.get.chunkSize

      partRequestResults <- putAllChunks(
        multipartId,
        importId,
        chunkSize,
        file,
        organization = organization,
        tokenHeader = tokenHeader
      )

      completeRequest = uploadCompleteRequest(
        importId,
        datasetId.value,
        organization,
        tokenHeader,
        sessionHeader
      )

      completeResponse <- sendCompleteRequest(completeRequest)
    } yield (partRequestResults, completeResponse, chunkSize)
  }

  def ioCollectionChunkUpload(
    files: List[File],
    datasetId: DatasetId
  )(implicit
    contextShift: ContextShift[IO] = blockingContextShift
  ) = {
    val timeTestStarted = ZonedDateTime.now(ZoneOffset.UTC)

    for {
      previewResponse <- {
        client.expectCatch[PreviewPackageResponse](
          ioNewPreviewRequest(collectionPreviewRequest(files))
        )
      }

      previewWithChunkResults <- {
        previewResponse.packages.map { packagePreview =>
          packagePreview.files
            .map { file =>
              val chunkSize = file.chunkedUpload.get.chunkSize
              val multipartId = file.multipartUploadId.get
              val nonZero = file.size == 0
              val importId = packagePreview.importId

              val localFile = files(file.uploadId.get)

              putAllChunks(multipartId, importId, chunkSize, localFile, nonZero = nonZero)
                .map(maybeResults => (file, maybeResults))
            }
            .sequence
            .map((packagePreview, _))
        }.parSequence
      }

      (previews, _) = previewWithChunkResults.unzip
      completeResponses <- {
        previews.map { preview =>
          val completeRequest =
            uploadCompleteRequest(preview.importId, datasetId.value)

          sendCompleteRequest(completeRequest)
        }.parSequence
      }

      packages <- {
        ioRecursePackagesOnDataset(datasetId)
          .map {
            _.filter(_.content.createdAt.isAfter(timeTestStarted))
          }
      }

    } yield {
      val folders =
        previews.flatMap(_.files.flatMap(_.filePath.zipWithIndex)).toSet.toSeq.unzip._1
      val previewPackageNames = previews.map(_.packageName) ++ folders
      val packageNames = packages.map(_.content.name)

      (previewWithChunkResults, completeResponses, packageNames, previewPackageNames)
    }
  }
}
