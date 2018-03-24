package sangria.gateway.schema.mat

import sangria.schema.AstSchemaResolver

trait DirectiveProvider {
  def resolvers: Seq[AstSchemaResolver[GatewayContext]]
}
