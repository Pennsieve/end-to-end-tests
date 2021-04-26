package com.blackfynn.integration.processing

import com.blackfynn.integration.Tags.{ Processing, Slow }
import com.blackfynn.integration.models.DatasetFixture
import com.blackfynn.models.PackageState.{ READY, UPLOADED }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.duration.DurationDouble

class ZipProcessingSpec extends WordSpec with Matchers with DatasetFixture {

  "A zip file should be processed" taggedAs (Slow, Processing) in withDataset(15 minutes) {
    datasetId =>
      val result =
        uploadAndAwaitProcessingResult(datasetId, "small.zip").value
          .unsafeRunTimed(15 minutes)

      result shouldBe Some(Right((UPLOADED, READY)))
  }

}
