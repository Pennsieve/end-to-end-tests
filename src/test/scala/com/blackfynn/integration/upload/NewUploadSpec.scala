package com.blackfynn.integration.upload

import java.io.File

import cats.implicits._
import com.blackfynn.integration.Tags._
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.models.PackagePreview._
import com.blackfynn.integration.models._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationDouble

class NewUploadSpec extends WordSpecLike with Matchers with DatasetFixture {

  "A chunked file sent with fine uploader should be uploaded" taggedAs Upload in withDataset {
    datasetId =>
      val ioResult = ioChunkUpload(largeZip, datasetId)

      val (partResults, completeResponse, _) = ioResult.unsafeRunTimed(3 minutes).get

      partResults.map(_.forall(_.success)) shouldBe Right(true)

      val uploadedDatasetId = completeResponse.map(_.map(_.length))

      // This doesn't matter we just want to see the error message if it exists
      uploadedDatasetId shouldBe Right(Right(1))
  }

  "A whole directory should be uploaded" taggedAs Upload in withDataset { datasetId =>
    val directory: File = createFile("DatasetTemplate")

    val files: List[File] = recurseAndFlattenFileTree(directory.listFiles().toList)

    val ioSamePackagesCreatedAsExpected = ioCollectionChunkUpload(files, datasetId)
    val (chunkResults, completeResponses, actualPackages, previewedPackages) =
      ioSamePackagesCreatedAsExpected.unsafeRunTimed(15 minutes).get

    chunkResults
      .foreach {
        case (_, results) =>
          results.foreach {
            case (_, maybeResults) =>
              maybeResults shouldBe an[Right[_, _]]
          }
      }

    completeResponses.exists(_.forall(_.isRight)) shouldBe true

    actualPackages should contain allElementsOf previewedPackages
  }

  "A package that contains lots of files should be uploaded" taggedAs Upload in withDataset {
    datasetId =>
      val (chunkResults, completeResponses, actualPackages, previewedPackages) =
        ioCollectionChunkUpload(dicomFiles, datasetId).unsafeRunTimed(3 minutes).get

      chunkResults
        .foreach {
          case (_, results) =>
            results.foreach {
              case (_, maybeResults) =>
                maybeResults shouldBe an[Right[_, _]]
            }
        }

      chunkResults.head._2.foreach {
        case (_, maybeResults) => maybeResults shouldBe a[Right[_, _]]
      }

      completeResponses.exists(_.forall(_.isRight)) shouldBe true

      // 6 includes the package itself 5 parent directories
      actualPackages.length shouldBe 6
      previewedPackages.length shouldBe 1
  }
}
