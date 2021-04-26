package com.blackfynn.integration.api

import com.blackfynn.dtos.DataSetDTO
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.Tags.Packages
import com.blackfynn.integration.models.Dataset._
import com.blackfynn.integration.models._
import com.blackfynn.models._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationDouble

class PackagePaginationSpec extends WordSpecLike with Matchers with DatasetFixture {

  "get page of packages" taggedAs Packages in {
    val ioPackagesPage =
      client
        .expectCatch[List[DataSetDTO]](ioAllDatasets)
        .map(datasets => DatasetId(datasets.head.content.id))
        .flatMap { datasetId =>
          client.expect[PackagesPage](ioDatasetPackages(datasetId))
        }

    ioPackagesPage.unsafeRunTimed(10 seconds).isDefined shouldBe true
  }

  "recurse page of packages" taggedAs Packages in {
    val ioPackagesPages =
      client
        .expectCatch[List[DataSetDTO]](ioAllDatasets)
        .map(datasets => DatasetId(datasets.head.content.id))
        .flatMap(ioRecursePackagesOnDataset)

    ioPackagesPages.unsafeRunTimed(30 seconds).isDefined shouldBe true
  }
}
