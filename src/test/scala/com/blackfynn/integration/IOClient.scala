package com.blackfynn.integration

import cats.effect.{ ContextShift, IO, Sync }
import cats.implicits._
import fs2.text.utf8Decode
import org.http4s.client.Client
import org.http4s.client.JavaNetClientBuilder
import org.http4s.{ EntityDecoder, Request, Response }

import scala.concurrent.ExecutionContext.Implicits.global

object IOClient {
  implicit class RichClient[F[_]: Sync](client: Client[F]) {
    private def exceptionFromBody: Response[F] => F[Throwable] =
      _.body.through(utf8Decode).compile.foldMonoid.map(new Exception(_))

    def expectCatch[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] =
      client.expectOr(req)(exceptionFromBody)

    def expectCatch[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
      client.expectOr(req)(exceptionFromBody)
  }

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  lazy val client: Client[IO] = JavaNetClientBuilder[IO](global).create
}
