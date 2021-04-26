package com.blackfynn.integration

import java.io.File

package object models {
  def createFile(name: String) = new File(s"src/test/resources/data/$name")
}
