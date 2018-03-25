package sangria.gateway.schema.materializer.directive

import sangria.gateway.schema.materializer.GatewayContext
import sangria.schema.{Action, AstSchemaResolver, Context}

trait DirectiveProvider {
  def resolvers(ctx: GatewayContext): Seq[AstSchemaResolver[GatewayContext]]
  def anyResolver: Option[PartialFunction[Context[GatewayContext, _], Action[GatewayContext, Any]]] = None
}
