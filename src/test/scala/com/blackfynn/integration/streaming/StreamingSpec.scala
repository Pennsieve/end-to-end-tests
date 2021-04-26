package com.blackfynn.integration.streaming

import com.blackfynn.integration.IOClient.client
import com.blackfynn.integration.Tags.Streaming
import com.blackfynn.integration.WsClient
import com.blackfynn.integration.UriCreator._
import com.blackfynn.integration.models.Login._
import com.blackfynn.streaming.TimeSeriesMessage
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.http4s.Method.GET
import org.http4s.client.dsl.io._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration.DurationLong

class StreamingSpec extends WordSpecLike with Matchers with Eventually {

  "streaming" should {
    "be healthy" taggedAs Streaming in {
      val healthCheckResult =
        client
          .successful(GET(toUri(s"/streaming/ts/health?session=$sessionToken")))
          .unsafeRunTimed(10 seconds)

      healthCheckResult shouldBe Some(true)
    }

    "stream timeseries data for a timeseries package" taggedAs Streaming ignore {
      WsClient.connect(s"/streaming/ts/query?session=$sessionToken&package=$DefaultPackageId") {
        wsClient =>
          wsClient.send(createTimeseriesRequest(sessionToken))

          // should get two messages - one per channel
          val channel1 = wsClient.expectMessage()
          val channel2 = wsClient.expectMessage()

          // uncomment to view errors
          // println(s"error: ${(message.map(_.toChar)).mkString}")

          val timeSeriesMessage1 = TimeSeriesMessage.parseFrom(channel1)
          val timeSeriesMessage2 = TimeSeriesMessage.parseFrom(channel2)

          timeSeriesMessage1.segment.get.data.length should be(3000)
          timeSeriesMessage2.segment.get.data.length should be(3000)
      }
    }

    "montage data for a montageable timeseries package" taggedAs Streaming ignore {
      eventually(Timeout(30 seconds)) {
        WsClient.connect(s"/streaming/ts/query?session=$sessionToken&package=$montageablePackageId") {
          wsClient =>
            val montageRequest =
              s"""
            {
              "packageId": "$montageablePackageId",
              "montage": "REFERENTIAL_VS_CZ"
            }
          """

            wsClient.send(montageRequest)

            val montageResponse = wsClient.expectMessage().map(_.toChar).mkString

            val virtualChannels =
              decode[Json](montageResponse).right.get.hcursor
                .downField("virtualChannels")
                .values
                .get
                .map { virtualChannel =>
                  Map(
                    "id" -> virtualChannel.hcursor.downField("id").as[String].right.get,
                    "name" -> virtualChannel.hcursor.downField("name").as[String].right.get
                  )
                }

            val timeSeriesRequest =
              createTimeseriesRequest(
                sessionToken,
                montageablePackageId,
                virtualChannels.asJson.noSpaces
              )

            wsClient.send(timeSeriesRequest)

            val timeSeriesResponses =
              Range(0, virtualChannels.size)
                .map(_ => TimeSeriesMessage.parseFrom(wsClient.expectMessage()))

            val responseNames = timeSeriesResponses.map(_.segment.get.channelName)
            val expectedNames = virtualChannels.map(_("name"))

            responseNames should contain theSameElementsAs expectedNames
        }
      }
    }

    "fail to montage data for an unmontageable package" taggedAs Streaming ignore {
      WsClient.connect(s"/streaming/ts/query?session=$sessionToken&package=$DefaultPackageId") {
        wsClient =>
          val montageRequest = s"""
            {
              "packageId": "$DefaultPackageId",
              "montage": "REFERENTIAL_VS_CZ"
            }
          """

          wsClient.send(montageRequest)

          val errorMessage = wsClient.expectMessage().map(_.toChar).mkString

          val cursor = decode[Json](errorMessage).right.get.hcursor

          val error = cursor.downField("error").as[String].right.get
          val channelNames = cursor.downField("channelNames").as[List[String]].right.get

          error should be("PackageCannotBeMontaged")
          channelNames should not be empty
      }
    }
  }
}
