package com.blackfynn.integration.models

import cats.effect.IO
import com.blackfynn.dtos.PackageDTO
import com.blackfynn.integration.IOClient.{ client, RichClient }
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.models.Login.{ bearerTokenHeader, sessionHeader }
import org.http4s.Header
import org.http4s.Method.{ GET, PUT }
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.dsl.io._
import cats.implicits._
import com.blackfynn.integration.trials._

object BfPackage {
  def getPackage(
    id: String,
    tokenHeader: Header = bearerTokenHeader,
    sessionHeader: Header = sessionHeader
  ): IO[PackageDTO] = {
    client.expectCatch[PackageDTO](GET(toUri(s"/packages/$id"), tokenHeader, sessionHeader))
  }

  def processPackage(
    id: String,
    tokenHeader: Header = bearerTokenHeader,
    sessionHeader: Header = sessionHeader
  ): IO[Either[Exception, Unit]] =
    client
      .successful(PUT(toUri(s"/packages/$id/process"), tokenHeader, sessionHeader))
      .map {
        case true => ().asRight
        case false => new Exception("failed to start processing").asLeft
      }
}
