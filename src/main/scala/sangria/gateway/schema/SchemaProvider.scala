package sangria.gateway.schema

import akka.NotUsed
import akka.stream.scaladsl.Source
import better.files.File
import io.circe.Json
import sangria.execution.{Executor, Middleware}
import sangria.execution.deferred.DeferredResolver
import sangria.schema.Schema

import scala.concurrent.{Await, Future}

trait SchemaProvider[Ctx, Val] {
  def schemaInfo: Future[Option[SchemaInfo[Ctx, Val]]]
  def schemaChanges: Option[Source[Boolean, NotUsed]]
}

case class SchemaInfo[Ctx, Val](
  schema: Schema[Ctx, Val],
  ctx: Ctx,
  value: Val,
  middleware: List[Middleware[Ctx]],
  deferredResolver: DeferredResolver[Ctx] = DeferredResolver.empty,
  schemaRendered: String,
  schemaIntrospection: Json,
  files: Vector[File])
