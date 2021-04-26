package com.blackfynn.integration.trials

import com.blackfynn.integration.BlackfynnEnvironment.{ Development, Production }
import com.blackfynn.integration.IOClient.client
import com.blackfynn.integration.Tags.Trials
import com.blackfynn.integration.UriCreator.toUri
import org.http4s.Method.GET
import org.http4s.client.dsl.io._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationLong

class TrialDefaultProxySpec extends WordSpecLike with Matchers {
  config.bfEnvironment match {
    case Development =>
      "A blind reviewer" should {
        "be able to get a list of organizations" taggedAs Trials in {
          val organizationsResult =
            client
              .successful(
                GET(
                  toUri(s"/trials/organizations"),
                  blindReviewerBearerTokenHeader,
                  blindReviewerSessionHeader
                )
              )
              .unsafeRunTimed(10 seconds)

          organizationsResult shouldBe Some(true)
        }

        "be able to get the current user" taggedAs Trials in {
          val userResult =
            client
              .successful(
                GET(
                  toUri(s"/trials/user"),
                  blindReviewerBearerTokenHeader,
                  blindReviewerSessionHeader
                )
              )
              .unsafeRunTimed(10 seconds)

          userResult shouldBe Some(true)
        }
      }
    case Production => Nil
  }
}
