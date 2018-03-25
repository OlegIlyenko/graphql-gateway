package sangria.gateway.schema.materializer

import sangria.schema._
import sangria.gateway.schema.materializer.directive.DirectiveProvider

import scala.concurrent.ExecutionContext

class GatewayMaterializer(directiveProviders: Seq[DirectiveProvider])(implicit ec: ExecutionContext) {
  def commonResolvers(ctx: GatewayContext, ar: Seq[PartialFunction[Context[GatewayContext, _], Action[GatewayContext, Any]]]) = Seq[AstSchemaResolver[GatewayContext]](
    AnyFieldResolver {
      case origin if !origin.isInstanceOf[ExistingSchemaOrigin[_, _]] ⇒ c ⇒ {
        val fn = ar.find(_.isDefinedAt(c)).getOrElse(throw new SchemaMaterializationException(s"Field resolver is not defined. Unable to handle value `${c.value}`."))

        fn(c)
      }
    })

  def schemaBuilder(ctx: GatewayContext) = {
    val anyResolvers = directiveProviders.flatMap(_.anyResolver)
    val resolvers = directiveProviders.flatMap(_.resolvers(ctx)) ++ commonResolvers(ctx, anyResolvers)

    AstSchemaBuilder.resolverBased[GatewayContext](resolvers: _*)
  }
}