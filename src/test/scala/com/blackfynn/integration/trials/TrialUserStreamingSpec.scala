package com.blackfynn.integration.trials

import com.blackfynn.dtos._
import com.blackfynn.integration.BlackfynnEnvironment.{ Development, Production }
import com.blackfynn.integration.IOClient
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.Tags.Trials
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.WsClient
import com.blackfynn.integration.models.Dataset._
import com.blackfynn.integration.models.PackagePreview._
import com.blackfynn.integration.models.TrialDatasetFixture
import com.blackfynn.integration.processing._
import com.blackfynn.models.PackagesPage
import com.blackfynn.streaming.TimeSeriesMessage
import org.http4s.Method.GET
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.dsl.io._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationDouble

class TrialUserStreamingSpec extends WordSpecLike with Matchers with TrialDatasetFixture {

  config.bfEnvironment match {
    case Development =>
      "send a request to stream time series data" taggedAs Trials ignore withTrialDataset() {
        //upload the correct file => test_generator.edf
        datasetId =>
          val processingResult =
            uploadAndAwaitProcessingResult(
              datasetId,
              testGeneratorName,
              40,
              ClinicalTrialsOrganization,
              trialUserTokenHeader,
              trialUserSessionHeader
            ).value
              .unsafeRunTimed(15 minutes)

          processingResult.isDefined shouldBe true

          val dts =
            client.expect[DataSetDTO](ioGetDatasetForTrial(datasetId)).unsafeRunTimed(3 minutes).get

          //get the package node id
          val pkgRq =
            client
              .expect[PackagesPage](ioDatasetPackagesForTrial(datasetId))
              .unsafeRunTimed(3 minutes)
              .get

          val channels = IOClient.client
            .fetchAs[List[ChannelDTO]](
              GET(
                toUri(s"/timeseries/${pkgRq.packages.head.content.nodeId}/channels"),
                trialUserTokenHeader,
                trialUserSessionHeader
              )
            )
            .unsafeRunTimed(5 minutes)

          val ClinicalTrialStreamingPackageChannel1Id = channels.head.head.content.id
          val ClinicalTrialStreamingPackageChannel1Name = channels.head.head.content.name
          val ClinicalTrialStreamingPackageChannel2Id = channels.head(2).content.id
          val ClinicalTrialStreamingPackageChannel2Name = channels.head(2).content.name

          val ClinicalTrialStreamingPackageVirtualChannels: String =
            s"""
               |[
               |  {
               |    "id": "$ClinicalTrialStreamingPackageChannel1Id",
               |    "name": "$ClinicalTrialStreamingPackageChannel1Name"
               |  }, {
               |    "id": "$ClinicalTrialStreamingPackageChannel2Id",
               |    "name": "$ClinicalTrialStreamingPackageChannel2Name"
               |  }
               |]
          """.stripMargin

          val streamingRequest =
            s"""
           {
             "session": "$blindReviewerSessionToken",
             "packageId": "${pkgRq.packages.head.content.nodeId}",
             "startTime": 0,
             "endTime": 30000000,
             "pixelWidth": 8592,
             "virtualChannels":$ClinicalTrialStreamingPackageVirtualChannels
           }
         """

          val path =
            s"/trials/${dts.content.intId}/ts/query?session=$blindReviewerSessionToken&package=${pkgRq.packages.head.content.nodeId}"

          WsClient.connect(path) { client =>
            client.send(streamingRequest)

            val firstChannel = TimeSeriesMessage.parseFrom(client.expectMessage(10 seconds))
            val secondChannel = TimeSeriesMessage.parseFrom(client.expectMessage(10 seconds))

            firstChannel.segment.get.data.length shouldBe 6000
            secondChannel.segment.get.data.length shouldBe 6000
          }
      }
    case Production => Nil
  }
}
