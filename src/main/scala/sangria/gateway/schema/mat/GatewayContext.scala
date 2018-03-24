package sangria.gateway.schema.mat

import language.existentials

import io.circe.Json
import io.circe.optics.JsonPath.root
import sangria.ast
import sangria.gateway.http.client.HttpClient
import sangria.gateway.json.CirceJsonPath
import sangria.gateway.util.Logging
import sangria.schema._
import sangria.schema.ResolverBasedAstSchemaBuilder.resolveDirectives
import sangria.marshalling.circe._
import sangria.marshalling.queryAst._
import sangria.marshalling.MarshallingUtil._

import scala.concurrent.{ExecutionContext, Future}

case class GatewayContext(client: HttpClient, vars: Json, graphqlIncludes: Vector[GraphQLIncludedSchema]) {
  import GatewayContext._
  
  val allTypes = graphqlIncludes.flatMap(_.types)

  def request(schema: GraphQLIncludedSchema, query: ast.Document, extractName: String)(implicit ec: ExecutionContext): Future[Json] = {
    val body = Json.obj("query" → Json.fromString(query.renderPretty))

    client.request(HttpClient.Method.Post, schema.include.url, body = Some("application/json" → body.noSpaces)).flatMap(GatewayContext.parseJson).map(resp ⇒
      root.data.at(extractName).getOption(resp).flatten.get)
  }

  def findFields(name: String, typeName: String, includeFields: Option[Seq[String]]): List[MaterializedField[GatewayContext, _]] =
    graphqlIncludes.find(_.include.name == name).toList.flatMap { s ⇒
      val tpe = s.schema.getOutputType(ast.NamedType(typeName), topLevel = true)
      val fields = tpe.toList
          .collect {case obj: ObjectLikeType[_, _] ⇒ obj}
          .flatMap { t ⇒
            val fields = includeFields match  {
              case Some(inc) ⇒ t.uniqueFields.filter(f ⇒ includeFields contains f.name)
              case _ ⇒ t.uniqueFields
            }

            fields.asInstanceOf[Vector[Field[GatewayContext, Any]]]
          }


      fields.map(f ⇒ MaterializedField(s, f.copy(astDirectives = Vector(ast.Directive("delegate", Vector.empty)))))
    }

  def fillPlaceholders(ctx: Context[GatewayContext, _], value: String, cachedArgs: Option[Json] = None, elem: Json = Json.Null): String = {
    lazy val args = cachedArgs getOrElse convertArgs(ctx.args, ctx.astFields.head)

    PlaceholderRegExp.findAllMatchIn(value).toVector.foldLeft(value) { case (acc, p) ⇒
      val placeholder = p.group(0)

      val idx = p.group(1).indexOf(".")

      if (idx < 0) throw new IllegalStateException(s"Invalid placeholder '$placeholder'. It should contain two parts: scope (like value or ctx) and extractor (name of the field or JSON path) separated byt dot (.).")

      val (scope, selectorWithDot) = p.group(1).splitAt(idx)
      val selector = selectorWithDot.substring(1)

      val source = scope match {
        case "value" ⇒ ctx.value.asInstanceOf[Json]
        case "ctx" ⇒ ctx.ctx.vars
        case "arg" ⇒ args
        case "elem" ⇒ elem
        case s ⇒ throw new IllegalStateException(s"Unsupported placeholder scope '$s'. Supported scopes are: value, ctx, arg, elem.")
      }

      val value =
        if (selector.startsWith("$"))
          render(CirceJsonPath.query(source, selector))
        else
          source.get(selector).map(render).getOrElse("")

      acc.replace(placeholder, value)
    }
  }
}

object GatewayContext extends Logging {
  import GatewayMaterializer._
  
  def parseJson(resp: HttpClient.HttpResponse)(implicit ec: ExecutionContext) =
    if (resp.isSuccessful)
      resp.asString.map(s ⇒ {
        io.circe.parser.parse(s).fold({ e ⇒
          logger.error(s"Failed to parse JSON response for successful request (${resp.statusCode} ${resp.debugInfo}). Body: $s", e)

          throw e
        }, identity)
      })
    else
      Future.failed(new IllegalStateException(s"Failed HTTP request with status code ${resp.statusCode}. ${resp.debugInfo}"))

  val rootValueLoc = Set(DirectiveLocation.Schema)

  def rootValue(schemaAst: ast.Document) = {
    val values = resolveDirectives(schemaAst,
      GenericDirectiveResolver(Dirs.JsonConst, rootValueLoc,
        c ⇒ Some(io.circe.parser.parse(c arg Args.JsonValue).fold(throw _, identity))),
      GenericDynamicDirectiveResolver[Json, Json]("const", rootValueLoc,
        c ⇒ c.args.get("value")))

    Json.fromFields(values.collect {
      case json: Json if json.isObject ⇒ json.asObject.get.toIterable
    }.flatten)
  }

  def graphqlIncludes(schemaAst: ast.Document) =
    resolveDirectives(schemaAst,
      GenericDirectiveResolver(Dirs.IncludeGraphQL, resolve =
          c ⇒ Some(c.arg(Args.Schemas).map(s ⇒ GraphQLInclude(s("url").asInstanceOf[String], s("name").asInstanceOf[String]))))).flatten

  def loadIncludedSchemas(client: HttpClient, includes: Vector[GraphQLInclude])(implicit ec: ExecutionContext): Future[Vector[GraphQLIncludedSchema]] = {
    val loaded =
      includes.map { include ⇒
        val introspectionBody = Json.obj("query" → Json.fromString(sangria.introspection.introspectionQuery.renderPretty))

        client.request(HttpClient.Method.Post, include.url, body = Some("application/json" → introspectionBody.noSpaces)).flatMap(parseJson).map(resp ⇒
          GraphQLIncludedSchema(include, Schema.buildFromIntrospection(resp)))
      }

    Future.sequence(loaded)
  }

  def loadContext(client: HttpClient, schemaAst: ast.Document)(implicit ec: ExecutionContext): Future[GatewayContext] = {
    val includes = graphqlIncludes(schemaAst)
    val vars = rootValue(schemaAst)

    loadIncludedSchemas(client, includes).map(GatewayContext(client, vars, _))
  }

  def namedType(tpe: OutputType[_]): OutputType[_] = tpe match {
    case ListType(of) ⇒ namedType(of)
    case OptionType(of) ⇒ namedType(of)
    case t ⇒ t
  }

  def convertArgs(args: Args, field: ast.Field): Json =
    Json.fromFields(args.raw.keys.flatMap(name ⇒ field.arguments.find(_.name == name).map(a ⇒ a.name → a.value.convertMarshaled[Json])))

  private val PlaceholderRegExp = """\$\{([^}]+)\}""".r

  // TODO: improve :)
  def render(value: Json) = value.fold(
    jsonNull = "null",
    jsonBoolean = "" + _,
    jsonNumber = "" + _.toBigDecimal.get,
    jsonString = identity,
    jsonArray = "" + _,
    jsonObject = "" + _)

  implicit class JsonOps(value: Json) {
    def get(key: String) = value match {
      case json: Json if json.isObject ⇒ json.asObject.get(key)
      case _ ⇒ None
    }
  }
}