package sangria.gateway

import better.files.File
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration.FiniteDuration

case class WatchConfig(
  enabled: Boolean,
  paths: Seq[String],
  pathsStr: Option[String],
  threshold: FiniteDuration,
  glob: Seq[String],
  globStr: Option[String]
) {
  lazy val allPaths = pathsStr.map(_.split("\\s*,\\s*").toSeq) getOrElse paths
  lazy val allFiles = allPaths.map(File(_))
  lazy val allGlobs = globStr.map(_.split("\\s*,\\s*").toSeq) getOrElse glob
}

case class LimitConfig(
  complexity: Double,
  maxDepth: Int,
  allowIntrospection: Boolean)

case class SlowLogConfig(
  enabled: Boolean,
  threshold: FiniteDuration,
  extension: Boolean,
  apolloTracing: Boolean)

case class AppConfig(
  port: Int,
  bindHost: String,
  graphiql: Boolean,
  slowLog: SlowLogConfig,
  watch: WatchConfig,
  limit: LimitConfig,
  includeDirectives: Option[Seq[String]],
  excludeDirectives: Option[Seq[String]]
) {
  def isEnabled(directivesName: String) =
    !excludeDirectives.exists(_.contains(directivesName)) && (
      includeDirectives.isEmpty ||
      includeDirectives.exists(_.contains(directivesName)))
}

object AppConfig {
  def load(config: Config): AppConfig = config.as[AppConfig]
}
