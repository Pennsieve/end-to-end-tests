ThisBuild / Test / fork := true

val AwsVersion = "1.11.414"
val Http4sVersion = "0.20.3"
val CirceVersion = "0.11.1"


lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin)
  .settings(
    inThisBuild(List(
      organization := "com.blackfynn",
      scalaVersion := "2.12.11",
      version := sys.props.get("version").getOrElse("SNAPSHOT"),
      // Temporarily disable Coursier because parallel builds fail on Jenkins.
      // See https://app.clickup.com/t/a8ned9
      useCoursier := false ,
      scalacOptions ++= Seq(
        "-language:implicitConversions",
        "-language:postfixOps",
        "-Ypartial-unification",
        "-Xmax-classfile-name", "100",
        "-feature",
        "-deprecation",
        "-language:higherKinds",
      ),
      credentials += Credentials(
        "Sonatype Nexus Repository Manager",
        "nexus.blackfynn.cc",
        sys.env("BLACKFYNN_NEXUS_USER"),
        sys.env("BLACKFYNN_NEXUS_PW")
      ),
      resolvers ++= Seq(
        "blackfynn-maven-proxy" at "https://nexus.blackfynn.cc/repository/maven-public",
        Resolver.url("blackfynn-ivy-proxy", url("https://nexus.blackfynn.cc/repository/ivy-public/"))( Patterns("[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]") ),
        Resolver.bintrayRepo("hseeberger", "maven"),
        Resolver.jcenterRepo,
        Resolver.bintrayRepo("commercetools", "maven")
      ),
    )),

    addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
    addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4"),

    name := "end-to-end-tests",
    libraryDependencies ++= Seq(
      "com.amazonaws"             % "aws-java-sdk-s3"       % AwsVersion        % Test,
      "com.blackfynn"            %% "timeseries-core"       % "1.2.11"          % Test,
      "com.blackfynn"            %% "core-models"           % "35-73e9630"      % Test,
      "io.circe"                 %% "circe-core"            % CirceVersion      % Test,
      "io.circe"                 %% "circe-generic"         % CirceVersion      % Test,
      "io.circe"                 %% "circe-java8"           % CirceVersion      % Test,
      "com.github.pureconfig"    %% "pureconfig"            % "0.11.0"          % Test,
      "com.github.pureconfig"    %% "pureconfig-enumeratum" % "0.11.0"          % Test,
      "org.http4s"               %% "http4s-blaze-client"   % Http4sVersion     % Test,
      "org.http4s"               %% "http4s-circe"          % Http4sVersion     % Test,
      "org.http4s"               %% "http4s-client"         % Http4sVersion     % Test,
      "org.http4s"               %% "http4s-core"           % Http4sVersion     % Test,
      "org.java-websocket"        % "Java-WebSocket"        % "1.4.0"           % Test,
      "org.scalatest"            %% "scalatest"             % "3.0.5"           % Test,
    ),

    scalafmtOnCompile := true,

    inConfig(Test)(baseAssemblySettings),
    test in (Test, assembly) := {},
    // Discard data resources, they are copied directly into the Docker container
    assemblyMergeStrategy in (Test, assembly) := {
      case PathList("data", _ @_*) => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },

    imageNames in docker := Seq(ImageName(s"blackfynn/end-to-end-tests:${version.value}")),
    dockerfile in docker := {
      val artifact: File = (Test / assembly).value // Note: uses TEST assembly
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("blackfynn/java-cloudwrap:8-jre-alpine-0.5.5")
        workDir("/app")
        // Data files must be copied in because there is not a sane way to load
        // directories from JAR file resources.
        copy(baseDirectory(_ / "src/test/resources/data").value, "/app/src/test/resources/data")
        copy(artifact, artifactTargetPath)
        entryPoint("java",  "-cp", artifactTargetPath, "org.scalatest.tools.Runner", "-o", "-R", artifactTargetPath)
      }
    }
  )
