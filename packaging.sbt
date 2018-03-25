import com.typesafe.sbt.packager.docker.Cmd

import scala.sys.process._

dockerBaseImage := "frolvlad/alpine-oraclejdk8:8.161.12-slim"
version in Docker := ("git rev-parse HEAD" !!).trim
dockerRepository := Some("tenshi")
dockerUpdateLatest := true
dockerExposedVolumes := Seq(s"/schema")
dockerExposedPorts := Seq(8080)

dockerCommands := Seq(
  dockerCommands.value.head,
  // Install bash to be able to start the application
  Cmd("RUN apk add --update bash && rm -rf /var/cache/apk/*")
) ++ dockerCommands.value.tail

dockerCommands += Cmd("ENV", "WATCH_PATHS=/schema")

assemblyJarName := "graphql-gateway.jar"

enablePlugins(JavaServerAppPackaging, DockerPlugin)