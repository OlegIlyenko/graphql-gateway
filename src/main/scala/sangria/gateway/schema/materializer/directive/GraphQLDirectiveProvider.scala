package sangria.gateway.schema.materializer.directive

import sangria.gateway.schema.materializer.GatewayContext
import sangria.schema._

class GraphQLDirectiveProvider extends DirectiveProvider {
  import GraphQLDirectiveProvider._

  def resolvers(ctx: GatewayContext) = Seq(
    AdditionalDirectives(Seq(Dirs.IncludeGraphQL)),
    AdditionalTypes(ctx.allTypes.toList),

    DirectiveFieldProvider(Dirs.IncludeField, _.withArgs(Args.Fields) { fields ⇒
      fields.toList.flatMap { f ⇒
        val name = f("schema").asInstanceOf[String]
        val typeName = f("type").asInstanceOf[String]
        val includes = f.get("fields").asInstanceOf[Option[Option[Seq[String]]]].flatten

        ctx.findFields(name, typeName, includes)
      }
    }))
}

object GraphQLDirectiveProvider {
  object Args {
    val IncludeType = InputObjectType("GraphQLSchemaInclude", fields = List(
      InputField("name", StringType),
      InputField("url", StringType)))

    val IncludeFieldsType = InputObjectType("GraphQLIncludeFields", fields = List(
      InputField("schema", StringType),
      InputField("type", StringType),
      InputField("fields", OptionInputType(ListInputType(StringType)))))

    val Schemas = Argument("schemas", ListInputType(IncludeType))
    val Fields = Argument("fields", ListInputType(IncludeFieldsType))
  }

  object Dirs {
    val IncludeGraphQL = Directive("includeGraphQL",
      arguments = Args.Schemas :: Nil,
      locations = Set(DirectiveLocation.Schema))

    val IncludeField = Directive("include",
      arguments = Args.Fields :: Nil,
      locations = Set(DirectiveLocation.Object))
  }
}


