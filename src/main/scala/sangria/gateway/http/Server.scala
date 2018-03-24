package sangria.gateway.http

import language.postfixOps
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sangria.gateway.AppConfig
import sangria.gateway.http.client.AkkaHttpClient
import sangria.gateway.schema.mat.GatewayMaterializer
import sangria.gateway.schema.{ReloadableSchemaProvider, StaticSchemaProvider}
import sangria.gateway.util.Logging

import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object Server extends App with Logging {
  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  try {
    val config = AppConfig.load(ConfigFactory.load())
    val client = new AkkaHttpClient
    val gatewayMaterializer = new GatewayMaterializer(client)

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
      case Failure(_) ⇒ system.terminate()
    }
  } catch {
    case NonFatal(error) ⇒
      logger.error("Error during server startup", error)
      system.terminate()
  }
}