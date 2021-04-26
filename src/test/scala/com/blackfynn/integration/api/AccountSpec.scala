package com.blackfynn.integration.api

import com.blackfynn.integration.Tags.Authentication
import com.blackfynn.integration.models.Login._
import com.blackfynn.integration.models.LoginResponse
import org.scalatest.{ Matchers, WordSpecLike }

class AccountSpec extends WordSpecLike with Matchers {

  "A user should be able to log in" taggedAs Authentication in {
    loginResponse shouldBe a[LoginResponse]
  }
}
