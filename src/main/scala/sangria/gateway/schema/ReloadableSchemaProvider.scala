package sangria.gateway.schema

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{BroadcastHub, Keep, RunnableGraph, Source}
import better.files.File
import sangria.gateway.AppConfig
import sangria.gateway.file.FileMonitorActor
import sangria.gateway.http.client.HttpClient
import sangria.gateway.schema.materializer.{GatewayContext, GatewayMaterializer}
import sangria.gateway.util.Logging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// TODO: on a timer reload all external schemas and check for changes
class ReloadableSchemaProvider(config: AppConfig, client: HttpClient, mat: GatewayMaterializer)(implicit system: ActorSystem, ec: ExecutionContext, amat: Materializer) extends SchemaProvider[GatewayContext, Any] with Logging {
  val loader = new SchemaLoader(config, client, mat)
  val schemaRef = new AtomicReference[Option[SchemaInfo[GatewayContext, Any]]](None)

  system.actorOf(FileMonitorActor.props(config.watch.allFiles, config.watch.threshold, config.watch.allGlobs, reloadSchema))

  private val producer = Source.actorRef[Boolean](100, OverflowStrategy.dropTail)
  private val runnableGraph = producer.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
  private val (changesPublisher, changesSource) = runnableGraph.run()

  val schemaChanges = Some(changesSource)

  def schemaInfo =
    schemaRef.get() match {
      case v @ Some(_) ⇒ Future.successful(v)
      case None ⇒ reloadSchema
    }

  def reloadSchema(files: Vector[File]): Unit = {
    logger.info(s"Schema files are changed: ${files mkString ", "}. Reloading schema")

    reloadSchema
  }

  def reloadSchema: Future[Option[SchemaInfo[GatewayContext, Any]]] =
    loader.loadSchema.andThen {
      case Success(Some(newSchema)) ⇒
        schemaRef.get() match {
          case Some(currentSchema) ⇒
            val changes = newSchema.schema.compare(currentSchema.schema)
            val renderedChanges =
              if (changes.nonEmpty)
                " with following changes:\n" + changes.map(c ⇒ "  * " + c.description + (if (c.breakingChange) " (breaking)" else "")).mkString("\n")
              else
                " without any changes."

            changesPublisher ! true
            logger.info(s"Schema successfully reloaded$renderedChanges")
          case None ⇒
            logger.info(s"Schema successfully loaded from files:\n${newSchema.files.map(f ⇒ "  * " + f).mkString("\n")}")
        }

        schemaRef.set(Some(newSchema))
      case Failure(error) ⇒
        logger.error("Failed to load the schema", error)
    }
}
