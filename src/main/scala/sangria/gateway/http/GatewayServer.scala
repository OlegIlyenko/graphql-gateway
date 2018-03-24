package sangria.gateway.http

import language.postfixOps
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sangria.gateway.AppConfig
import sangria.gateway.http.client.AkkaHttpClient
import sangria.gateway.schema.materializer.GatewayMaterializer
import sangria.gateway.schema.materializer.directive.{BasicDirectiveProvider, DirectiveProvider, GraphQLDirectiveProvider, HttpDirectiveProvider}
import sangria.gateway.schema.{ReloadableSchemaProvider, StaticSchemaProvider}
import sangria.gateway.util.Logging

import scala.util.{Failure, Success}
import scala.util.control.NonFatal

class GatewayServer extends Logging {
  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val client = new AkkaHttpClient

  val directiveProviders = Map(
    "http" → new HttpDirectiveProvider(client),
    "graphql" → new GraphQLDirectiveProvider,
    "basic" → new BasicDirectiveProvider)

  def startup(config: AppConfig) =
    try {
      val gatewayMaterializer = new GatewayMaterializer(filterDirectives(config, directiveProviders))

      val schemaProvider =
        if (config.watch.enabled)
          new ReloadableSchemaProvider(config, client, gatewayMaterializer)
        else
          new StaticSchemaProvider(config, client, gatewayMaterializer)

      schemaProvider.schemaInfo // trigger initial schema load at startup

      val routing = new GraphQLRouting(config, schemaProvider)

      Http().bindAndHandle(routing.route, config.bindHost, config.port).andThen {
        case Success(_) ⇒
          logger.info(s"Server started on ${config.bindHost}:${config.port}")

          if (config.watch.enabled)
            logger.info(s"Watching files at following path: ${config.watch.allFiles.mkString(", ")}. Looking for files: ${config.watch.allGlobs.mkString(", ")}.")

        case Failure(_) ⇒
          shutdown()
      }
    } catch {
      case NonFatal(error) ⇒
        logger.error("Error during server startup", error)

        shutdown()
    }

  def shutdown(): Unit = {
    logger.info("Shutting down server")
    system.terminate()
  }

  private def filterDirectives(config: AppConfig, providers: Map[String, DirectiveProvider]) = {
    val includes = config.includeDirectives.fold(Set.empty[String])(_.toSet)
    val excludes = config.includeDirectives.fold(Set.empty[String])(_.toSet)
    val initial = providers.toVector

    val withIncludes =
      if (config.includeDirectives.nonEmpty)
        initial.filter(dp ⇒ includes contains dp._1)
      else
        initial

    val withExcludes =
      if (config.excludeDirectives.nonEmpty)
        withIncludes.filterNot(dp ⇒ excludes contains dp._1)
      else
        withIncludes

    withExcludes.map(_._2)
  }
}