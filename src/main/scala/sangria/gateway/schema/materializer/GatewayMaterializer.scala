package sangria.gateway.schema.materializer

import language.existentials
import sangria.ast
import sangria.gateway.schema.CustomScalars
import sangria.schema._
import sangria.marshalling.queryAst._
import sangria.gateway.schema.materializer.directive.DirectiveProvider

import scala.concurrent.ExecutionContext

class GatewayMaterializer(directiveProviders: Seq[DirectiveProvider])(implicit ec: ExecutionContext) {
  def commonResolvers(ctx: GatewayContext, ar: Seq[PartialFunction[Context[GatewayContext, _], Action[GatewayContext, Any]]]) = Seq[AstSchemaResolver[GatewayContext]](
    ExistingFieldResolver {
      case (o: GraphQLIncludedSchema, _, f) if ctx.graphqlIncludes.exists(_.include.name == o.include.name) && f.astDirectives.exists(_.name == "delegate") ⇒
        val schema = ctx.graphqlIncludes.find(_.include.name == o.include.name).get

        c ⇒ {
          val query = ast.Document(Vector(ast.OperationDefinition(ast.OperationType.Query, selections = c.astFields)))

          ctx.request(schema, query, c.astFields.head.outputName)
        }
    },
    
    ExistingScalarResolver {
      case ctx if ctx.existing.name != CustomScalars.DateTimeType.name ⇒
        ctx.existing.copy(
          coerceUserInput = Right(_),
          coerceOutput = (v, _) ⇒ v,
          coerceInput = v ⇒ Right(queryAstInputUnmarshaller.getScalaScalarValue(v)))
    },

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