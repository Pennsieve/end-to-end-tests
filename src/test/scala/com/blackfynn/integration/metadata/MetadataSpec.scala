package com.blackfynn.integration.metadata

import com.blackfynn.dtos._
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.Tags.Metadata
import com.blackfynn.integration.models.Dataset._
import com.blackfynn.integration.models.Model._
import com.blackfynn.integration.models.{ DatasetFixture, DatasetId }
import io.circe._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{ Matchers, WordSpecLike }

import java.time.ZonedDateTime
import scala.concurrent.duration._

class MetadataSpec extends WordSpecLike with Matchers with DatasetFixture {

  implicit val zonedDateTimeOrdering: Ordering[ZonedDateTime] =
    Ordering.by(_.toInstant)

  "creating a model should touch dataset updatedAt timestamp" taggedAs Metadata in withDataset {
    datasetId =>
      val io = for {
        dataset <- client.expectCatch[DataSetDTO](ioGetDataset(datasetId))

        model <- client.expectCatch[Json](ioCreateModel(datasetId, "patient"))

        updatedDataset <- client.expectCatch[DataSetDTO](ioGetDataset(datasetId))

      } yield dataset.content.updatedAt should be < updatedDataset.content.updatedAt

      io.unsafeRunSync
  }
}
