package sangria.gateway.schema.materializer

import language.existentials
import sangria.ast
import sangria.schema._
import sangria.marshalling.circe._
import sangria.marshalling.queryAst._
import io.circe._
import sangria.gateway.schema.materializer.directive.DirectiveProvider

import scala.concurrent.ExecutionContext

class GatewayMaterializer(directiveProviders: Seq[DirectiveProvider])(implicit ec: ExecutionContext) {
  def commonResolvers(ctx: GatewayContext) = Seq[AstSchemaResolver[GatewayContext]](
    ExistingFieldResolver {
      case (o: GraphQLIncludedSchema, _, f) if ctx.graphqlIncludes.exists(_.include.name == o.include.name) && f.astDirectives.exists(_.name == "delegate") ⇒
        val schema = ctx.graphqlIncludes.find(_.include.name == o.include.name).get

        c ⇒ {
          val query = ast.Document(Vector(ast.OperationDefinition(ast.OperationType.Query, selections = c.astFields)))

          ctx.request(schema, query, c.astFields.head.outputName)
        }
    },
    
    ExistingScalarResolver {
      case ctx ⇒ ctx.existing.copy(
        coerceUserInput = Right(_),
        coerceOutput = (v, _) ⇒ v,
        coerceInput = v ⇒ Right(queryAstInputUnmarshaller.getScalaScalarValue(v)))
    },

    AnyFieldResolver.defaultInput[GatewayContext, Json])

  def schemaBuilder(ctx: GatewayContext) = {
    val resolvers = directiveProviders.flatMap(_.resolvers(ctx)) ++ commonResolvers(ctx)

    AstSchemaBuilder.resolverBased[GatewayContext](resolvers: _*)
  }
}