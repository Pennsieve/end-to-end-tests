package com.blackfynn.integration

import org.scalatest.Tag

object Tags {

  case object Slow extends Tag("Slow")
  case object Discover extends Tag("Discover")
  case object Upload extends Tag("Upload")
  case object Processing extends Tag("Processing")
  case object Trials extends Tag("Trials")
  case object Streaming extends Tag("Streaming")
  case object Packages extends Tag("Packages")
  case object Authentication extends Tag("Authentication")
  case object Metadata extends Tag("Metadata")
}
