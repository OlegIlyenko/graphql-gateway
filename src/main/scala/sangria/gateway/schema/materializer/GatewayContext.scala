package sangria.gateway.schema.materializer

import java.util.{Locale, Random}

import com.github.javafaker.Faker

import language.existentials
import io.circe.Json
import io.circe.optics.JsonPath.root
import sangria.ast
import sangria.gateway.AppConfig
import sangria.gateway.http.client.HttpClient
import sangria.gateway.json.CirceJsonPath
import sangria.gateway.schema.materializer.directive.{BasicDirectiveProvider, FakerDirectiveProvider, GraphQLDirectiveProvider, OAuthClientCredentials}
import sangria.gateway.util.Logging
import sangria.schema._
import sangria.schema.ResolverBasedAstSchemaBuilder.resolveDirectives
import sangria.marshalling.circe._
import sangria.marshalling.queryAst._
import sangria.marshalling.MarshallingUtil._
import sangria.gateway.schema.materializer.directive.HttpDirectiveProvider.{extractDelegatedHeaders, extractMap}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

case class GatewayContext(client: HttpClient, rnd: Option[Random], faker: Option[Faker], vars: Json, graphqlIncludes: Vector[GraphQLIncludedSchema], operationName: Option[String] = None, queryVars: Json = Json.obj(), originalHeaders: Seq[(String, String)] = Seq.empty) {
  import GatewayContext._
  
  val allTypes = graphqlIncludes.flatMap(_.types)

  def request(c: Context[GatewayContext, _], schema: GraphQLIncludedSchema, query: ast.Document, variables: Json, extractName: String)(implicit ec: ExecutionContext): Future[Json] = {
    val fields = Vector("query" → Json.fromString(query.renderPretty))
    val withVars =
      if (variables != Json.obj()) fields :+ ("variables" → variables)
      else fields

    val body = Json.fromFields(withVars)

    val url = GatewayContext.fillPlaceholders(Some(c), schema.include.url)
    val queryParams = schema.include.queryParams
    val oauthHead = oauthHeaders(schema.oauthToken)
    val headers = oauthHead ++ schema.include.headers ++ extractDelegatedHeaders(c, Some(schema.include.delegateHeaders))

    client.request(HttpClient.Method.Post, url, queryParams, headers, Some("application/json" → body.noSpaces)).flatMap(GatewayContext.parseJson).map(resp ⇒
      root.data.at(extractName).getOption(resp).flatten.get)
  }

  def findFields(name: String, typeName: String, includeFields: Option[Seq[String]], excludeFields: Option[Seq[String]]): List[MaterializedField[GatewayContext, _]] =
    graphqlIncludes.find(_.include.name == name).toList.flatMap { s ⇒
      val tpe = s.schema.getOutputType(ast.NamedType(typeName), topLevel = true)
      val fields = tpe.toList
        .collect {case obj: ObjectLikeType[_, _] ⇒ obj}
        .flatMap { t ⇒
          val withIncludes = includeFields match  {
            case Some(inc) ⇒ t.uniqueFields.filter(f ⇒ includeFields.forall(_.contains(f.name)))
            case _ ⇒ t.uniqueFields
          }

          val withExcludes = excludeFields match  {
            case Some(excl) ⇒ withIncludes.filterNot(f ⇒ excl.contains(f.name))
            case _ ⇒ withIncludes
          }

          withExcludes.asInstanceOf[Vector[Field[GatewayContext, Any]]]
        }

      fields.map(f ⇒ MaterializedField(s, f.copy(astDirectives = Vector(ast.Directive("delegate", Vector.empty)))))
    }
}

object GatewayContext extends Logging {
  val envJson = Json.obj(System.getenv().asScala.mapValues(Json.fromString).toSeq: _*)

  def fillPlaceholders(ctx: Option[Context[GatewayContext, _]], value: String, cachedArgs: Option[Json] = None, elem: Json = Json.Null): String = {
    lazy val args = cachedArgs orElse ctx.map(c ⇒ convertArgs(c.args, c.astFields.head)) getOrElse Json.obj()

    PlaceholderRegExp.findAllMatchIn(value).toVector.foldLeft(value) { case (acc, p) ⇒
      val placeholder = p.group(0)

      val idx = p.group(1).indexOf(".")

      if (idx < 0) throw new IllegalStateException(s"Invalid placeholder '$placeholder'. It should contain two parts: scope (like value or ctx) and extractor (name of the field or JSON path) separated byt dot (.).")

      val (scope, selectorWithDot) = p.group(1).splitAt(idx)
      val selector = selectorWithDot.substring(1)

      def unsupported(s: String) = throw new IllegalStateException(s"Unsupported placeholder scope '$s'. Supported scopes are: value, ctx, arg, elem.")

      val source = scope match {
        case "value" ⇒ ctx.getOrElse(unsupported("value")).value.asInstanceOf[Json]
        case "ctx" ⇒ ctx.getOrElse(unsupported("ctx")).ctx.vars
        case "arg" ⇒
          ctx.getOrElse(unsupported("arg"))
          args
        case "elem" ⇒ elem
        case "env" ⇒ envJson
        case s ⇒ unsupported(s)
      }

      val value =
        if (selector.startsWith("$"))
          render(CirceJsonPath.query(source, selector))
        else
          source.get(selector).map(render).getOrElse("")

      acc.replace(placeholder, value)
    }
  }

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
      GenericDirectiveResolver(BasicDirectiveProvider.Dirs.JsonConst, rootValueLoc,
        c ⇒ Some(io.circe.parser.parse(c arg BasicDirectiveProvider.Args.JsonValue).fold(throw _, identity))),
      GenericDynamicDirectiveResolver[Json, Json]("const", rootValueLoc,
        c ⇒ c.args.get("value")))

    Json.fromFields(values.collect {
      case json: Json if json.isObject ⇒ json.asObject.get.toIterable
    }.flatten)
  }

  def graphqlIncludes(schemaAst: ast.Document) = {
    import GraphQLDirectiveProvider.{Args, Dirs}

    resolveDirectives(schemaAst,
      GenericDirectiveResolver(Dirs.IncludeSchema, resolve = c ⇒
        c.withArgs(Args.Name, Args.Url, Args.Headers, Args.DelegateHeaders, Args.QueryParams, Args.OAuth)((name, url, headers, delegateHeaders, queryParams, oauth) ⇒
          Some(GraphQLInclude(url, name, extractMap(None, headers), extractMap(None, queryParams), delegateHeaders getOrElse Seq.empty, oauth)))))
  }

  def fakerConfig(schemaAst: ast.Document) = {
    import sangria.gateway.schema.materializer.directive.FakerDirectiveProvider._

    resolveDirectives(schemaAst,
      GenericDirectiveResolver(Dirs.FakeConfig,
        resolve = c ⇒ Some(c.arg(Args.Locale).map(Locale.forLanguageTag) → c.arg(Args.Seed)))).headOption.getOrElse(None → None)
  }

  def loadIncludedSchemas(client: HttpClient, includes: Vector[GraphQLInclude])(implicit ec: ExecutionContext): Future[Vector[GraphQLIncludedSchema]] = {
    val loaded =
      includes.map { include ⇒
        include.oauth match {
          case Some(oauth) ⇒
            for {
              token ← loadOAuthToken(client, oauth)
              resp ← loadSchemaIntorospection(client, include, token)
            } yield GraphQLIncludedSchema(include, Schema.buildFromIntrospection(resp), token)

          case None ⇒
            loadSchemaIntorospection(client, include, None)
              .map(resp ⇒ GraphQLIncludedSchema(include, Schema.buildFromIntrospection(resp), None))
        }
      }

    Future.sequence(loaded)
  }

  def loadSchemaIntorospection(client: HttpClient, include: GraphQLInclude, oauthToken: Option[String])(implicit ec: ExecutionContext) = {
    val introspectionBody = Json.obj("query" → Json.fromString(sangria.introspection.introspectionQuery(schemaDescription = false, directiveRepeatableFlag = false).renderPretty))
    val url = GatewayContext.fillPlaceholders(None, include.url)
    val oauthHead = oauthHeaders(oauthToken)

    client.request(HttpClient.Method.Post, url, include.queryParams, include.headers ++ oauthHead, Some("application/json" → introspectionBody.noSpaces)).flatMap(parseJson)
  }

  def oauthHeaders(oauthToken: Option[String]) = oauthToken match {
    case Some(token) ⇒ Seq("Authorization" → s"Bearer $token")
    case _ ⇒ Seq.empty
  }

  def loadOAuthToken(client: HttpClient, credentials: OAuthClientCredentials)(implicit ec: ExecutionContext) = {
    val url = GatewayContext.fillPlaceholders(None, credentials.url)
    val clientId = GatewayContext.fillPlaceholders(None, credentials.clientId)
    val clientSecret = GatewayContext.fillPlaceholders(None, credentials.clientSecret)
    val scopes = credentials.scopes.map(GatewayContext.fillPlaceholders(None, _))

    client.oauthClientCredentials(url, clientId, clientSecret, scopes).flatMap(parseJson)
      .map(json ⇒ json.asObject.get.apply("access_token").flatMap(_.asString))
  }

  def loadContext(config: AppConfig, client: HttpClient, schemaAst: ast.Document)(implicit ec: ExecutionContext): Future[GatewayContext] = {
    val includes = graphqlIncludes(schemaAst)
    val vars = rootValue(schemaAst)
    val (fakerLocale, fakerSeed) = fakerConfig(schemaAst)

    val (rnd, faker) =
      if (config isEnabled "faker") {
        val rnd = fakerSeed.fold(new Random(System.currentTimeMillis()))(s ⇒ new Random(s.toLong))
        
        Some(rnd) → Some(fakerLocale.fold(new Faker(rnd))(l ⇒ new Faker(l, rnd)))
      } else None → None

    loadIncludedSchemas(client, includes).map(GatewayContext(client, rnd, faker, vars, _))
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

case class GraphQLInclude(url: String, name: String, headers: Seq[(String, String)], queryParams: Seq[(String, String)], delegateHeaders: Seq[String], oauth: Option[OAuthClientCredentials])
case class GraphQLIncludedSchema(include: GraphQLInclude, schema: Schema[_, _], oauthToken: Option[String]) extends MatOrigin {
  private val rootTypeNames = Set(schema.query.name) ++ schema.mutation.map(_.name).toSet ++ schema.subscription.map(_.name).toSet

  val types = schema.allTypes.values
    .filterNot(t ⇒ Schema.isBuiltInType(t.name) || rootTypeNames.contains(t.name)).toVector
    .map {
      case t: ObjectType[_, _] ⇒
        t.withInstanceCheck((value, _, tpe) ⇒ value match {
          case json: Json ⇒ root.__typename.string.getOption(json).contains(tpe.name)
          case _ ⇒ false
        })

      case t ⇒ t
    }
    .map(MaterializedType(this, _))

  def description = s"included schema '${include.name}'"
}
