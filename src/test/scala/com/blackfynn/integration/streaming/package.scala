package com.blackfynn.integration

package object streaming {

  val DefaultPackageId: String = "N:package:13a482a4-d5b4-4b91-aabb-c899ebe2ef97"

  val DefaultPackageChannel1 = "N:channel:32f345c8-0379-4b65-b638-a1d503c693ea"
  val DefaultPackageChannel2 = "N:channel:563329db-af37-44e0-9e1d-c693f49b844f"

  val defaultVirtualChannels: String =
    s"""
      |[
      |  {
      |    "id": "$DefaultPackageChannel1",
      |    "name": "sin-01hz"
      |  }, {
      |    "id": "$DefaultPackageChannel2",
      |    "name": "sin-10hz"
      |  }
      |]
    """.stripMargin

  val montageablePackageId = "N:package:c6fb8f53-3c3a-4ef2-82f3-068ccf5b7132"

  def createTimeseriesRequest(
    sessionToken: String,
    packageId: String = DefaultPackageId,
    virtualChannels: String = defaultVirtualChannels
  ) =
    s"""
       {
         "session": "$sessionToken",
         "packageId": "$packageId",
         "startTime": 0,
         "endTime": 30000000,
         "pixelWidth": 8592,
         "virtualChannels":$virtualChannels
       }
     """

}
