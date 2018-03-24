package sangria.gateway.schema

import sangria.gateway.AppConfig
import sangria.gateway.schema.mat.{GatewayContext, GatewayMaterializer}

import scala.concurrent.ExecutionContext

class StaticSchemaProvider(config: AppConfig, mat: GatewayMaterializer)(implicit ec: ExecutionContext) extends SchemaProvider[GatewayContext, Any] {
  val loader = new SchemaLoader(config, mat)
  
  val schemaInfo = loader.loadSchema
}