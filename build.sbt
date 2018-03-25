name := "graphql-gateway"
organization := "org.sangria-graphql"
version := "0.1.0-SNAPSHOT"

description := "GraphQL Gateway - SDL-based GraphQL gateway for REST and GraphQL-based microservices"
homepage := Some(url("http://sangria-graphql.org"))
licenses := Seq("Apache License, ASL Version 2.0" → url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.12.4"

scalacOptions ++= Seq("-deprecation", "-feature")

mainClass in Compile := Some("sangria.gateway.Main")

val sangriaVersion = "1.4.0"
val circeVersion = "0.9.2"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % sangriaVersion,
  "org.sangria-graphql" %% "sangria-slowlog" % "0.1.5",
  "org.sangria-graphql" %% "sangria-circe" % "1.2.1",

  "com.typesafe.akka" %% "akka-http" % "10.1.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.11",
  "de.heikoseeberger" %% "akka-http-circe" % "1.20.0",
  "de.heikoseeberger" %% "akka-sse" % "3.0.0",

  "com.github.pathikrit"  %% "better-files-akka"  % "3.4.0",

  "io.circe" %%	"circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-optics" % circeVersion,

  "com.jayway.jsonpath" % "json-path" % "2.3.0",

  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.javafaker" % "javafaker" % "0.14",
  "info.henix" %% "ssoup" % "0.5",

  "org.slf4j" % "slf4j-api" % "1.7.25",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

// nice *magenta* prompt!

shellPrompt in ThisBuild := { state ⇒
  scala.Console.MAGENTA + Project.extract(state).currentRef.project + "> " + scala.Console.RESET
}