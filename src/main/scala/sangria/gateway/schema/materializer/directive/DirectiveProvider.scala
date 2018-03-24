package sangria.gateway.schema.materializer.directive

import sangria.gateway.schema.materializer.GatewayContext
import sangria.schema.AstSchemaResolver

trait DirectiveProvider {
  def resolvers(ctx: GatewayContext): Seq[AstSchemaResolver[GatewayContext]]
}
