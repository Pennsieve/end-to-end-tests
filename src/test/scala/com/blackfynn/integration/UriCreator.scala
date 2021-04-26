package com.blackfynn.integration

import org.http4s.Uri
import com.blackfynn.integration.BlackfynnEnvironment._

object UriCreator {
  private val devUri: Uri = Uri.uri("https://api.blackfynn.net")
  private val wsDevUri: Uri = Uri.uri("wss://api.blackfynn.net")
  private val prodUri: Uri = Uri.uri("https://api.blackfynn.io")
  private val wsProdUri: Uri = Uri.uri("wss://api.blackfynn.io")

  val config: EndToEndConfig = EndToEndConfig.loadOrThrow

  def toUri(pathString: String): Uri =
    config.bfEnvironment match {
      case Production => prodUri.withPath(pathString)
      case Development => devUri.withPath(pathString)
    }

  def toWSUri(pathString: String): Uri =
    config.bfEnvironment match {
      case Production => wsProdUri.withPath(pathString)
      case Development => wsDevUri.withPath(pathString)
    }
}
