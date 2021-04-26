package com.blackfynn.integration.models

import cats.effect.IO
import com.blackfynn.dtos.UserDTO
import com.blackfynn.integration.{ EndToEndConfig, IOClient, UriCreator }
import io.circe.Decoder
import io.circe.java8.time.decodeZonedDateTime
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax.EncoderOps
import org.http4s.Credentials.Token
import org.http4s.Method.POST
import org.http4s.{ AuthScheme, Header, Request, Uri }
import org.http4s.circe._
import org.http4s.client.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.circe.CirceEntityDecoder._

import scala.concurrent.duration.DurationInt

case class LoginRequest(email: String, password: String)

case class LoginResponse(
  sessionToken: String,
  organization: String,
  profile: UserDTO,
  message: String
)

object Login {
  val loginUri: Uri = UriCreator.toUri("/account/login")

  implicit val userDTODecoder: Decoder[UserDTO] = deriveDecoder[UserDTO]
  implicit val responseDecoder: Decoder[LoginResponse] = deriveDecoder[LoginResponse]

  val config: EndToEndConfig = EndToEndConfig.loadOrThrow

  def postLoginRequest(email: String, password: String, uri: Uri = loginUri): IO[Request[IO]] =
    POST(LoginRequest(email, password).asJson, loginUri)

  lazy val loginResponse: LoginResponse =
    IOClient.client
      .expect[LoginResponse](postLoginRequest(config.email, config.password))
      .unsafeRunTimed(1 minute)
      .get

  lazy val sessionToken: String = loginResponse.sessionToken

  lazy val sessionHeader: Header = Header("X-SESSION-ID", sessionToken)

  lazy val organization: String = loginResponse.organization

  lazy val bearerTokenHeader: Header = Authorization(Token(AuthScheme.Bearer, sessionToken))
}
