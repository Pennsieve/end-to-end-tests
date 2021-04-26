package com.blackfynn.integration.upload

import java.nio.file.{ Files, Paths }
import java.security.MessageDigest

import cats.effect.{ ContextShift, IO }
import com.blackfynn.integration.IOClient.{ client, RichClient }
import com.blackfynn.integration.models.{ DatasetFixture, GetFileHashResponse }
import com.blackfynn.integration.models.PackagePreview._
import com.blackfynn.integration.models.UploadHashing._
import com.blackfynn.integration.Tags.Upload
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.duration.DurationInt

class UploadHashingSpec extends WordSpec with Matchers with DatasetFixture {
  implicit val contextShift: ContextShift[IO] = blockingContextShift

  "Hashes for a chunked file sent with fine uploader should be cached" taggedAs Upload in withDataset {
    datasetId =>
      // upload a file
      val (_, completeResponse, chunkSize) =
        ioChunkUpload(largeZip, datasetId).unsafeRunTimed(3 minutes).get

      // get the filehash according to the upload service
      val importId = completeResponse.right.get.right.get.head.manifest.importId
      val GetFileHashResponse(hash) = client
        .expectCatch[GetFileHashResponse](ioGetFileHashRequest(importId.toString, largeZip.getName))
        .unsafeRunTimed(3 minutes)
        .get

      // compute the expected hash for this file
      val expected = Files
        .readAllBytes(Paths.get(largeZip.getAbsolutePath))
        .grouped(chunkSize.toInt)
        .map { chunk =>
          val digest = MessageDigest.getInstance("SHA-256")
          digest.update(chunk)
          encodeHex(digest.digest())
        }
        .foldLeft(MessageDigest.getInstance("SHA-256")) { (fullHashDigest, chunkHash) =>
          fullHashDigest.update(chunkHash.getBytes)
          fullHashDigest
        }
        .digest()

      // the two hashes should be equal
      hash shouldBe encodeHex(expected)
  }
}
