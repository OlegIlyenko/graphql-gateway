import scala.sys.process._

dockerBaseImage := "frolvlad/alpine-oraclejdk8:8.161.12-slim"
version in Docker := ("git rev-parse HEAD" !!)
dockerRepository := Some("tenshi")
dockerUpdateLatest := true
dockerExposedVolumes := Seq(s"/schema/")
dockerExposedPorts := Seq(8080)

enablePlugins(JavaServerAppPackaging, DockerPlugin)