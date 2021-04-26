package com.blackfynn.integration

import enumeratum._
import enumeratum.EnumEntry._
import pureconfig.generic.auto._
import pureconfig.module.enumeratum._

sealed trait BlackfynnEnvironment extends EnumEntry with Uppercase

object BlackfynnEnvironment extends Enum[BlackfynnEnvironment] {
  val values = findValues
  case object Development extends BlackfynnEnvironment
  case object Production extends BlackfynnEnvironment
}

case class EndToEndConfig(
  email: String,
  password: String,
  blindReviewerEmail: String,
  blindReviewerPassword: String,
  trialUserEmail: String,
  trialUserPassword: String,
  bfEnvironment: BlackfynnEnvironment
)

object EndToEndConfig {
  def loadOrThrow: EndToEndConfig = pureconfig.loadConfigOrThrow[EndToEndConfig]
}
