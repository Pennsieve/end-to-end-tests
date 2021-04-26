package com.blackfynn.integration.trials

import cats.implicits._
import com.blackfynn.integration.BlackfynnEnvironment.{ Development, Production }
import com.blackfynn.integration.EndToEndConfig
import com.blackfynn.integration.IOClient._
import com.blackfynn.integration.Tags.Trials
import com.blackfynn.integration.UriCreator._
import org.http4s.Method.GET
import org.http4s.client.dsl.io._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationLong

class TrialUserSpec extends WordSpecLike with Matchers {
  config.bfEnvironment match {
    case Development =>
      "A trial user" should {
        "be able to access the trials app" taggedAs Trials in {
          val healthCheckResult =
            client
              .expectCatch[String](GET(toUri("/trials/health"), blindReviewerBearerTokenHeader))
              .recover {
                case e => e.getMessage
              }
              .unsafeRunTimed(10 seconds)

          healthCheckResult shouldBe Some("OK")
        }
      }
    case Production => Nil
  }
}
