package sangria.gateway.schema.mat

import language.existentials
import sangria.ast
import sangria.gateway.http.client.HttpClient
import sangria.gateway.json.CirceJsonPath
import sangria.schema._
import sangria.marshalling.circe._
import sangria.marshalling.queryAst._
import sangria.schema.ResolverBasedAstSchemaBuilder.extractValue
import io.circe._
import io.circe.optics.JsonPath._
import sangria.gateway.schema.mat.GatewayContext._

import scala.concurrent.{ExecutionContext, Future}

class GatewayMaterializer(client: HttpClient)(implicit ec: ExecutionContext) {
  import GatewayMaterializer._
  
  def schemaBuilder(ctx: GatewayContext) = AstSchemaBuilder.resolverBased[GatewayContext](
    AdditionalDirectives(Seq(Dirs.IncludeGraphQL)),
    AdditionalTypes(ctx.allTypes.toList),

    DirectiveResolver(Dirs.Context, c ⇒ c.withArgs(Args.NameOpt, Args.Path) { (name, path) ⇒
      name
        .map(n ⇒ extractValue(c.ctx.field.fieldType, c.ctx.ctx.vars.get(n)))
        .orElse(path.map(p ⇒ extractValue(c.ctx.field.fieldType, Some(CirceJsonPath.query(c.ctx.ctx.vars, p)))))
        .getOrElse(throw SchemaMaterializationException(s"Can't find a directive argument 'path' or 'name'."))
    }),

    DirectiveResolver(Dirs.Value, c ⇒ c.withArgs(Args.NameOpt, Args.Path) { (name, path) ⇒
      def extract(value: Any) =
        name
          .map(n ⇒ extractValue(c.ctx.field.fieldType, value.asInstanceOf[Json].get(n)))
          .orElse(path.map(p ⇒ extractValue(c.ctx.field.fieldType, Some(CirceJsonPath.query(value.asInstanceOf[Json], p)))))
          .getOrElse(throw SchemaMaterializationException(s"Can't find a directive argument 'path' or 'name'."))

      c.lastValue map (_.map(extract)) getOrElse extract(c.ctx.value)
    }),

    DirectiveResolver(Dirs.Arg, c ⇒
      extractValue(c.ctx.field.fieldType,
        convertArgs(c.ctx.args, c.ctx.astFields.head).get(c arg Args.NameReq))),

    DynamicDirectiveResolver[GatewayContext, Json]("const", c ⇒
      extractValue(c.ctx.field.fieldType, Some(c.args.get("value") match {
        case Some(json: Json) if json.isString ⇒ Json.fromString(c.ctx.ctx.fillPlaceholders(c.ctx, json.asString.get))
        case Some(jv) ⇒ jv
        case _ ⇒ Json.Null
      }))),

    DirectiveResolver(Dirs.JsonConst, c ⇒
      extractValue(c.ctx.field.fieldType,
        Some(io.circe.parser.parse(c.ctx.ctx.fillPlaceholders(c.ctx, c arg Args.JsonValue)).fold(throw _, identity)))),

    DirectiveResolver(Dirs.HttpGet,
      complexity = Some(_ ⇒ (_, _, _) ⇒ 1000.0),
      resolve = c ⇒ c.withArgs(Args.Url, Args.Headers, Args.QueryParams, Args.ForAll) { (rawUrl, rawHeaders, rawQueryParams, forAll) ⇒
        val args = Some(convertArgs(c.ctx.args, c.ctx.astFields.head))

        def extractMap(in: Option[scala.Seq[InputObjectType.DefaultInput]], elem: Json) =
          rawHeaders.map(_.map(h ⇒ h("name").asInstanceOf[String] → c.ctx.ctx.fillPlaceholders(c.ctx, h("value").asInstanceOf[String], args, elem))).getOrElse(Nil)

        def makeRequest(tpe: OutputType[_], c: Context[GatewayContext, _], args: Option[Json], elem: Json = Json.Null) = {
          val url = c.ctx.fillPlaceholders(c, rawUrl, args, elem)
          val headers = extractMap(rawHeaders, elem)
          val query = extractMap(rawQueryParams, elem)

          client.request(HttpClient.Method.Get, url, query, headers).flatMap(GatewayContext.parseJson)
        }

        forAll match {
          case Some(elem) ⇒
            CirceJsonPath.query(c.ctx.value.asInstanceOf[Json], elem) match {
              case json: Json if json.isArray ⇒
                Future.sequence(json.asArray.get.map(e ⇒ makeRequest(namedType(c.ctx.field.fieldType), c.ctx, args, e))) map { v ⇒
                  extractValue(c.ctx.field.fieldType, Some(Json.arr(v.asInstanceOf[Seq[Json]]: _*)))
                }
              case e ⇒
                makeRequest(c.ctx.field.fieldType, c.ctx, args, e)
            }
          case None ⇒
            makeRequest(c.ctx.field.fieldType, c.ctx, args)
        }
      }),

    ExistingFieldResolver {
      case (o: GraphQLIncludedSchema, _, f) if ctx.graphqlIncludes.exists(_.include.name == o.include.name) && f.astDirectives.exists(_.name == "delegate") ⇒
        val schema = ctx.graphqlIncludes.find(_.include.name == o.include.name).get

        c ⇒ {
          val query = ast.Document(Vector(ast.OperationDefinition(ast.OperationType.Query, selections = c.astFields)))

          ctx.request(schema, query, c.astFields.head.outputName)
        }
    },

    DirectiveFieldProvider(Dirs.IncludeField, _.withArgs(Args.Fields) { fields ⇒
      fields.toList.flatMap { f ⇒
        val name = f("schema").asInstanceOf[String]
        val typeName = f("type").asInstanceOf[String]
        val includes = f.get("fields").asInstanceOf[Option[Option[Seq[String]]]].flatten

        ctx.findFields(name, typeName, includes)
      }
    }),

    ExistingScalarResolver {
      case ctx ⇒ ctx.existing.copy(
        coerceUserInput = Right(_),
        coerceOutput = (v, _) ⇒ v,
        coerceInput = v ⇒ Right(queryAstInputUnmarshaller.getScalaScalarValue(v)))
    },

    AnyFieldResolver.defaultInput[GatewayContext, Json])
}

object GatewayMaterializer {
  object Args {
    val HeaderType = InputObjectType("Header", fields = List(
      InputField("name", StringType),
      InputField("value", StringType)))

    val QueryParamType = InputObjectType("QueryParam", fields = List(
      InputField("name", StringType),
      InputField("value", StringType)))

    val IncludeType = InputObjectType("GraphQLSchemaInclude", fields = List(
      InputField("name", StringType),
      InputField("url", StringType)))

    val IncludeFieldsType = InputObjectType("GraphQLIncludeFields", fields = List(
      InputField("schema", StringType),
      InputField("type", StringType),
      InputField("fields", OptionInputType(ListInputType(StringType)))))

    val NameOpt = Argument("name", OptionInputType(StringType))
    val NameReq = Argument("name", StringType)
    val Path = Argument("path", OptionInputType(StringType))
    val JsonValue = Argument("value", StringType)
    val Url = Argument("url", StringType)
    val Headers = Argument("headers", OptionInputType(ListInputType(HeaderType)))
    val QueryParams = Argument("query", OptionInputType(ListInputType(QueryParamType)))
    val ForAll = Argument("forAll", OptionInputType(StringType))
    val Schemas = Argument("schemas", ListInputType(IncludeType))
    val Fields = Argument("fields", ListInputType(IncludeFieldsType))
  }

  object Dirs {
    val Context = Directive("context",
      arguments = Args.NameOpt :: Args.Path :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val Value = Directive("value",
      arguments = Args.NameOpt :: Args.Path :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val Arg = Directive("arg",
      arguments = Args.NameReq :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val JsonConst = Directive("jsonValue",
      arguments = Args.JsonValue :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition, DirectiveLocation.Schema))

    val HttpGet = Directive("httpGet",
      arguments = Args.Url :: Args.Headers :: Args.QueryParams :: Args.ForAll :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val IncludeGraphQL = Directive("includeGraphQL",
      arguments = Args.Schemas :: Nil,
      locations = Set(DirectiveLocation.Schema))

    val IncludeField = Directive("include",
      arguments = Args.Fields :: Nil,
      locations = Set(DirectiveLocation.Object))
  }
}


case class GraphQLInclude(url: String, name: String)
case class GraphQLIncludedSchema(include: GraphQLInclude, schema: Schema[_, _]) extends MatOrigin {
  private val rootTypeNames = Set(schema.query.name) ++ schema.mutation.map(_.name).toSet ++ schema.subscription.map(_.name).toSet

  val types = schema.allTypes.values
    .filterNot(t ⇒ Schema.isBuiltInType(t.name) || rootTypeNames.contains(t.name)).toVector
    .map(MaterializedType(this, _))

  def description = s"included schema '${include.name}'"
}