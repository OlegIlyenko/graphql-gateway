package sangria.gateway.schema

import better.files.File
import sangria.ast.Document
import sangria.execution.Executor
import sangria.execution.deferred.DeferredResolver
import sangria.gateway.AppConfig
import sangria.gateway.file.FileUtil
import sangria.gateway.schema.mat.{GatewayContext, GatewayMaterializer}
import sangria.gateway.util.Logging
import sangria.parser.QueryParser
import sangria.schema.Schema
import sangria.marshalling.circe._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class SchemaLoader(config: AppConfig, mat: GatewayMaterializer)(implicit ec: ExecutionContext) extends Logging {
  def loadSchema: Future[Option[SchemaInfo[GatewayContext, Any]]] = {
    val files = FileUtil.loadFiles(config.watch.allFiles, config.watch.allGlobs)

    if (files.nonEmpty) {
      val parsed =
        files.map {
          case (path, content) ⇒ path → QueryParser.parse(content)
        }

      val failed = parsed.collect {case (path, Failure(e)) ⇒ path → e}

      if (failed.nonEmpty) {
        failed.foreach { case (path, error) ⇒
          logger.error(s"Can't parse file '$path':\n${error.getMessage}")
        }

        Future.successful(None)
      } else {
        val successful = parsed.collect {case (path, Success(doc)) ⇒ path → doc}
        val document = Document.merge(successful.map(_._2))

        try {
          val info =
            for {
              ctx ← mat.loadContext(document)
              schema = Schema.buildFromAst(document, mat.schemaBuilder(ctx).validateSchemaWithException(document))
              intro ← executeIntrospection(schema, ctx)
            } yield Some(SchemaInfo(
              schema,
              ctx,
              (),
              Nil,
              DeferredResolver.empty,
              schema.renderPretty,
              intro,
              files.map(_._1)))

          info.recover(handleError(files))
        } catch {
          case e if handleError(files).isDefinedAt(e) ⇒
            Future.successful(handleError(files)(e))
        }
      }
    } else {
      logger.error("No schema files found!")
      Future.successful(None)
    }
  }

  private def handleError(files: Vector[(File, String)]): PartialFunction[Throwable, Option[SchemaInfo[GatewayContext, Any]]] = {
    case NonFatal(e) ⇒
      logger.error(s"Can't create the schema from files: ${files.map(_._1).mkString(", ")}. " + e.getMessage)
      None
  }

  private def executeIntrospection(schema: Schema[GatewayContext, Any], ctx: GatewayContext) =
    Executor.execute(schema, sangria.introspection.introspectionQuery, ctx)
}
