resolvers ++= Seq(
  "blackfynn-maven-proxy" at "https://nexus.blackfynn.cc/repository/maven-public",
  Resolver.url("blackfynn-ivy-proxy", url("https://nexus.blackfynn.cc/repository/ivy-public/"))( Patterns("[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]") ),
)

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "nexus.blackfynn.cc",
  sys.env("BLACKFYNN_NEXUS_USER"),
  sys.env("BLACKFYNN_NEXUS_PW")
)

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
