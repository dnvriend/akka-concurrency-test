organization := "com.github.dnvriend"

name := "akka-concurrency-test"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.2"

resolvers += "spray" at "http://repo.spray.io/"

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++=
  {	val scalaV = "2.11.2"
    val akkaV = "2.3.6"
    val sprayV = "1.3.1"
    val shapelessV = "2.0.0"
    val jsonV = "1.2.6"    
    Seq(
      "org.scala-lang"       % "scala-library"                % scalaV withSources() withJavadoc(),
      "org.scala-lang"       % "scala-actors"                 % "2.10.2" withSources() withJavadoc(),
      "com.typesafe.akka"   %% "akka-actor"                   % akkaV withSources() withJavadoc(),
      "com.typesafe.akka"   %% "akka-slf4j"                   % akkaV withSources() withJavadoc(),
      "ch.qos.logback"       % "logback-classic"              % "1.1.2",
      "com.github.dnvriend" %% "akka-persistence-inmemory"    % "0.0.2",
      "org.scalikejdbc"     %% "scalikejdbc"                  % "2.1.1",
      "postgresql"           % "postgresql"                   % "9.1-901.jdbc4",
      "io.spray"            %% "spray-httpx"                  % sprayV,
      "io.spray"            %% "spray-routing-shapeless2"     % sprayV,
      "io.spray"            %% "spray-util"                   % sprayV,
      "io.spray"            %% "spray-io"                     % sprayV,
      "io.spray"            %% "spray-can"                    % sprayV,
      "io.spray"            %% "spray-client"                 % sprayV,
      "io.spray"            %% "spray-json"                   % jsonV
    )
  }

autoCompilerPlugins := true

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

publishMavenStyle := true

publishArtifact in Test := false

net.virtualvoid.sbt.graph.Plugin.graphSettings
