package com.blackfynn.integration.trials

import com.blackfynn.dtos.{ ChannelDTO, DataSetDTO }
import com.blackfynn.integration.BlackfynnEnvironment.{ Development, Production }
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.Tags.Trials
import com.blackfynn.integration.UriCreator.toUri
import org.http4s.Method.GET
import org.http4s.client.dsl.io._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationLong

class TrialElevatedProxySpec extends WordSpecLike with Matchers {
  config.bfEnvironment match {
    case Development =>
      "A blind reviewer" should {
        "be able to get a list of organization members via organization role elevation" taggedAs Trials in {
          val organizationsResult =
            client
              .successful(
                GET(
                  toUri(s"/trials/organizations/$ClinicalTrialsOrganization/members"),
                  blindReviewerBearerTokenHeader,
                  blindReviewerSessionHeader
                )
              )
              .unsafeRunTimed(10 seconds)

          organizationsResult shouldBe Some(true)
        }

        "be able to get a package via dataset role elevation" taggedAs Trials in {
          val organizationsResult =
            client
              .successful(
                GET(
                  toUri(
                    s"/trials/$ClinicalTrialsEndToEndTestsDatasetIntId/packages/$ClinicalTrialsPackage"
                  ),
                  blindReviewerBearerTokenHeader,
                  blindReviewerSessionHeader
                )
              )
              .unsafeRunTimed(10 seconds)

          organizationsResult shouldBe Some(true)
        }

        "get datasets for a trials user" taggedAs Trials in {
          val datasets =
            client
              .expectCatch[List[DataSetDTO]](
                GET(toUri("/trials/datasets"), blindReviewerBearerTokenHeader)
              )
              .unsafeRunTimed(5 seconds)

          datasets shouldBe an[Some[DataSetDTO]]

          datasets.get should not be empty
        }

        "get timeseries data starting at epoch" taggedAs Trials ignore {
          val channels = client
            .fetchAs[List[ChannelDTO]](
              GET(
                toUri(
                  s"/trials/$ClinicalTrialsEndToEndTestsDatasetIntId/timeseries/$ClinicalTrialStreamingPackage/channels"
                ),
                blindReviewerBearerTokenHeader,
                blindReviewerSessionHeader
              )
            )
            .unsafeRunTimed(10 seconds)

          channels shouldBe an[Some[List[ChannelDTO]]]
          channels.get should not be empty

          channels.get.map(_.content.start) should contain only 0
          channels.get.map(_.content.end) should contain only 9999990000L
          channels.get.map(_.content.lastAnnotation) should contain only 0
        }
      }
    case Production => Nil
  }
}
