name := "graphql-gateway"
organization := "org.sangria-graphql"
version := "0.1.0-SNAPSHOT"

description := "GraphQL Gateway - SDL-based GraphQL gateway for REST and GraphQL-based microservices"
homepage := Some(url("http://sangria-graphql.org"))
licenses := Seq("Apache License, ASL Version 2.0" → url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.12.7"

scalacOptions ++= Seq("-deprecation", "-feature")

mainClass in Compile := Some("sangria.gateway.Main")

val sangriaVersion = "1.4.3-SNAPSHOT"
val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % sangriaVersion,
  "org.sangria-graphql" %% "sangria-slowlog" % "0.1.8",
  "org.sangria-graphql" %% "sangria-circe" % "1.2.1",

  "com.typesafe.akka" %% "akka-http" % "10.1.5",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.17",
  "de.heikoseeberger" %% "akka-http-circe" % "1.22.0",
  "de.heikoseeberger" %% "akka-sse" % "3.0.0",

  "com.github.pathikrit"  %% "better-files-akka"  % "3.6.0",

  "io.circe" %%	"circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-optics" % circeVersion,

  "com.jayway.jsonpath" % "json-path" % "2.4.0",

  "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.1.10",

  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.javafaker" % "javafaker" % "0.16",
  "info.henix" %% "ssoup" % "0.5",

  "org.slf4j" % "slf4j-api" % "1.7.25",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

// nice *magenta* prompt!

shellPrompt in ThisBuild := { state ⇒
  scala.Console.MAGENTA + Project.extract(state).currentRef.project + "> " + scala.Console.RESET
}
