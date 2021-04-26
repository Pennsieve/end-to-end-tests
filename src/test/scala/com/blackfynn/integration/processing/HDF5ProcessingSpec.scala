package com.blackfynn.integration.processing

import cats.implicits._
import com.blackfynn.integration.IOClient.cs
import com.blackfynn.integration.Tags.{ Processing, Slow }
import com.blackfynn.integration.models.DatasetFixture
import com.blackfynn.models.PackageState.{ READY, UPLOADED }
import org.scalatest.time.SpanSugar.convertDoubleToGrainOfTime
import org.scalatest.{ Matchers, WordSpec }

class HDF5ProcessingSpec extends WordSpec with Matchers with DatasetFixture {

  "A hdf5 type file" should {
    "be processed" taggedAs (Slow, Processing) in withDataset(15 minutes) { datasetId =>
      val results =
        List("test.hdf5", "test.nwb")
          .map(fileName => uploadAndAwaitProcessingResult(datasetId, fileName).value)
          .parSequence
          .unsafeRunTimed(15 minutes)

      results.isDefined shouldBe true

      results.get should contain only Right((UPLOADED, READY))
    }
  }

}
