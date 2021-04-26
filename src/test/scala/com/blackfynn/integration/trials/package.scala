package com.blackfynn.integration

import com.blackfynn.dtos.{ BlackfynnTermsOfServiceDTO, CustomTermsOfServiceDTO, OrcidDTO, UserDTO }
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.models.Login.postLoginRequest
import com.blackfynn.integration.models.LoginResponse
import com.blackfynn.models.Degree
import org.http4s.Credentials.Token
import org.http4s.{ AuthScheme, Header }
import org.http4s.headers.Authorization
import org.http4s.circe.CirceEntityDecoder._
import models.Login._
import java.time.ZonedDateTime

import scala.concurrent.duration.DurationInt

package object trials {
  // IDs in the clinical trials org
  val ClinicalTrialsOrganization = "N:organization:203f938f-e770-48d5-a9f3-2a855f04894e"

  val ClinicalTrialsEndToEndTestsDataset = "N:dataset:6687b273-3ecd-4ebc-bc24-7171db10ee3a"
  val ClinicalTrialsEndToEndTestsDatasetIntId = 1

  val ClinicalTrialStreamingPackage = "N:package:0d180e78-fbd8-4dd7-b033-22da8830bb54"
  val ClinicalTrialsPackage = "N:package:fafac7b5-7484-466b-89e5-49f900885d25"

  val ClinicalTrialStreamingPackageChannel1Id = "N:channel:412413d9-706c-4032-b6ba-001c2b1c3f74"
  val ClinicalTrialStreamingPackageChannel1Name = "sin-01hz"
  val ClinicalTrialStreamingPackageChannel2Id = "N:channel:1615b41e-48bb-424b-aac6-108f56d28278"
  val ClinicalTrialStreamingPackageChannel2Name = "sin-10hz"

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

  val config: EndToEndConfig = EndToEndConfig.loadOrThrow

  def createClinicalTrialTimeseriesRequest(
    sessionToken: String,
    packageId: String = ClinicalTrialStreamingPackage,
    virtualChannels: String = ClinicalTrialStreamingPackageVirtualChannels
  ) =
    s"""
       {
         "session": "$sessionToken",
         "packageId": "$packageId",
         "startTime": 0,
         "endTime": 30000000,
         "pixelWidth": 8592,
         "virtualChannels":$virtualChannels
       }
     """

  lazy val trialUserLoginResponse: LoginResponse = {
    if (config.bfEnvironment == BlackfynnEnvironment.Development) {
      IOClient.client
        .expect[LoginResponse](
          postLoginRequest(
            config.trialUserEmail,
            config.trialUserPassword,
            toUri("/trials/account/login")
          )
        )
        .unsafeRunTimed(1 minute)
        .get
    } else {
      new LoginResponse(
        "sessionToken",
        "organization",
        new UserDTO(
          "id",
          "email",
          "firstName",
          None,
          "lastName",
          None,
          "credential",
          "color",
          "url",
          1,
          false,
          ZonedDateTime.now,
          ZonedDateTime.now,
          None,
          None,
          None,
          Seq.empty,
          storage = None,
          intId = 1
        ),
        "message"
      )
    }
  }

  lazy val trialUserSessionToken: String = trialUserLoginResponse.sessionToken

  lazy val trialUserTokenHeader: Header =
    Authorization(Token(AuthScheme.Bearer, trialUserSessionToken))

  lazy val trialUserOrganization: String = trialUserLoginResponse.organization

  lazy val trialUserSessionHeader: Header = Header("X-SESSION-ID", trialUserSessionToken)

  lazy val blindReviewerLoginResponse: LoginResponse = {
    println(s"config.bfEnvironment: ${config.bfEnvironment}")
    println(
      s"config.bfEnvironment == BlackfynnEnvironment.Development: ${config.bfEnvironment == BlackfynnEnvironment.Development}"
    )

    if (config.bfEnvironment == BlackfynnEnvironment.Development) {
      IOClient.client
        .expect[LoginResponse](
          postLoginRequest(
            config.blindReviewerEmail,
            config.blindReviewerPassword,
            toUri("/trials/account/login")
          )
        )
        .unsafeRunTimed(1 minute)
        .get
    } else {
      new LoginResponse(
        "sessionToken",
        "organization",
        new UserDTO(
          "id",
          "email",
          "firstName",
          None,
          "lastName",
          None,
          "credential",
          "color",
          "url",
          1,
          false,
          ZonedDateTime.now,
          ZonedDateTime.now,
          None,
          None,
          None,
          Seq.empty,
          storage = None,
          intId = 1
        ),
        "message"
      )
    }
  }

  lazy val blindReviewerSessionToken: String = blindReviewerLoginResponse.sessionToken

  lazy val blindReviewerBearerTokenHeader: Header =
    Authorization(Token(AuthScheme.Bearer, blindReviewerSessionToken))

  lazy val blindReviewerOrganization: String = blindReviewerLoginResponse.organization

  lazy val blindReviewerSessionHeader: Header = Header("X-SESSION-ID", blindReviewerSessionToken)
}
