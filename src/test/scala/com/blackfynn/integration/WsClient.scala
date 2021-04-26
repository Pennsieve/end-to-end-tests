package com.blackfynn.integration

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

import cats.effect.{ IO, Timer }
import com.blackfynn.integration.UriCreator._
import org.http4s.Uri
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.scalatest.compatible.Assertion
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

final class WsClient(uri: Uri) extends WebSocketClient(new URI(uri.renderString)) with Matchers {

  private val DefaultTimeout = 5 seconds

  private val responseQueue = new LinkedBlockingDeque[Array[Byte]]()

  override def onOpen(handshakedata: ServerHandshake): Unit = ()
  override def onMessage(message: ByteBuffer): Unit = responseQueue.addLast(message.array)
  override def onMessage(message: String): Unit = responseQueue.addLast(message.getBytes)
  override def onClose(code: Int, reason: String, remote: Boolean): Unit = ()

  override def onError(ex: Exception): Unit = fail(ex)

  private def getNextMessage(duration: Duration): Try[Option[Array[Byte]]] =
    Try(Option(responseQueue.pollFirst(duration.length, duration.unit)))

  def expectMessage(duration: Duration = DefaultTimeout): Array[Byte] =
    getNextMessage(duration) match {
      case Success(Some(array)) => array
      case Success(None) => fail("timeout exceeded while waiting for message")
      case Failure(ex) => fail(s"failed waiting for message ${ex.getMessage}")
    }

  def expectNoMessage(duration: Duration = DefaultTimeout): Unit =
    getNextMessage(duration) match {
      case Success(Some(_)) => fail("unexpected message recieved")
      case Success(None) => ()
      case Failure(ex) => fail(s"failed whilst waiting ${ex.getMessage}")
    }
}

object WsClient extends Matchers {
  implicit val timer: Timer[IO] = IO.timer(Implicits.global)

  def connect(uri: String)(f: WsClient => Assertion): Assertion = {
    val wsClient = new WsClient(toWSUri(uri))

    if (wsClient.connectBlocking() == false) fail(s"Could not connect websocket for $uri")

    try {
      f(wsClient)
    } finally {
      wsClient.closeBlocking()
    }
  }
}
