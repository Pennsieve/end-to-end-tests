package com.blackfynn.integration.models

import cats.effect.IO
import com.blackfynn.integration.UriCreator.toUri
import com.blackfynn.integration.models.Login._
import io.circe.generic.semiauto._
import io.circe.Decoder
import org.http4s.{ circe, EntityDecoder, Headers, Request }
import org.http4s.Method.GET

object UploadHashing {
  def ioGetFileHashRequest(importId: String, fileName: String): Request[IO] =
    Request[IO](
      GET,
      toUri(s"/upload/hash/id/${importId}?fileName=${fileName}"),
      headers = Headers.of(bearerTokenHeader, sessionHeader)
    )
}

case class GetFileHashResponse(hash: String)
object GetFileHashResponse {
  private val objectDecoder: Decoder[GetFileHashResponse] = deriveDecoder[GetFileHashResponse]
  private val stringDecoder: Decoder[GetFileHashResponse] = Decoder[String].map { hash =>
    GetFileHashResponse(hash)
  }
  implicit def decoder: Decoder[GetFileHashResponse] =
    List[Decoder[GetFileHashResponse]](objectDecoder, stringDecoder)
      .reduceLeft(_ or _)

  implicit val entityDecoder: EntityDecoder[IO, List[GetFileHashResponse]] =
    circe.jsonOf[IO, List[GetFileHashResponse]]
}
