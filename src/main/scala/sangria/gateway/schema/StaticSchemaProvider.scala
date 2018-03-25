package sangria.gateway.schema

import sangria.gateway.AppConfig
import sangria.gateway.http.client.HttpClient
import sangria.gateway.schema.materializer.{GatewayContext, GatewayMaterializer}

import scala.concurrent.ExecutionContext

class StaticSchemaProvider(config: AppConfig, client: HttpClient, mat: GatewayMaterializer)(implicit ec: ExecutionContext) extends SchemaProvider[GatewayContext, Any] {
  val loader = new SchemaLoader(config, client, mat)
  
  val schemaInfo = loader.loadSchema
  val schemaChanges = None
}