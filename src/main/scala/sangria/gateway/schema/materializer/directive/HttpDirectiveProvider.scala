package sangria.gateway.schema.materializer.directive

import language.existentials

import io.circe.Json
import sangria.gateway.http.client.HttpClient
import sangria.gateway.json.CirceJsonPath
import sangria.gateway.schema.materializer.GatewayContext
import sangria.gateway.schema.materializer.GatewayContext.{convertArgs, namedType}
import sangria.schema.ResolverBasedAstSchemaBuilder.extractValue
import sangria.schema._
import sangria.marshalling.circe._

import scala.concurrent.{ExecutionContext, Future}

class HttpDirectiveProvider(client: HttpClient)(implicit ec: ExecutionContext) extends DirectiveProvider {
  import HttpDirectiveProvider._

  def resolvers(ctx: GatewayContext) = Seq(
    DirectiveResolver(Dirs.HttpGet,
      complexity = Some(_ ⇒ (_, _, _) ⇒ 1000.0),
      resolve = c ⇒ c.withArgs(Args.Url, Args.Headers, Args.DelegateHeaders, Args.QueryParams, Args.ForAll) { (rawUrl, rawHeaders, delegateHeaders, rawQueryParams, forAll) ⇒
        val args = Some(convertArgs(c.ctx.args, c.ctx.astFields.head))

        def makeRequest(tpe: OutputType[_], c: Context[GatewayContext, _], args: Option[Json], elem: Json = Json.Null) = {
          val somec = Some(c)
          val url = GatewayContext.fillPlaceholders(somec, rawUrl, args, elem)
          val headers = extractMap(somec, rawHeaders, args, elem)
          val query = extractMap(somec, rawQueryParams, args, elem)
          val extraHeaders = extractDelegatedHeaders(c, delegateHeaders)

          client.request(HttpClient.Method.Get, url, query, headers ++ extraHeaders).flatMap(GatewayContext.parseJson)
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
      }))
}

object HttpDirectiveProvider {
  def extractMap(ctx: Option[Context[GatewayContext, _]], in: Option[Seq[InputObjectType.DefaultInput]], args: Option[Json] = None, elem: Json = Json.Null) = {
    in.map(_.map { h ⇒
      val name = h("name").asInstanceOf[String]
      val value = h("value").asInstanceOf[String]
      
      name → GatewayContext.fillPlaceholders(ctx, value, args, elem)
    }).getOrElse(Nil)
  }

  def extractDelegatedHeaders(ctx: Context[GatewayContext, _], delegateHeaders: Option[Seq[String]]) =
    delegateHeaders match {
      case Some(hs) ⇒ ctx.ctx.originalHeaders.filter(orig ⇒ hs.contains(orig._1))
      case None ⇒ Seq.empty
    }

  object Args {
    val HeaderType = InputObjectType("Header", fields = List(
      InputField("name", StringType),
      InputField("value", StringType)))

    val QueryParamType = InputObjectType("QueryParam", fields = List(
      InputField("name", StringType),
      InputField("value", StringType)))

    val Url = Argument("url", StringType)
    val Headers = Argument("headers", OptionInputType(ListInputType(HeaderType)))
    val DelegateHeaders = Argument("delegateHeaders", OptionInputType(ListInputType(StringType)),
      "Delegate headers from original gateway request to the downstream services.")
    val QueryParams = Argument("query", OptionInputType(ListInputType(QueryParamType)))
    val ForAll = Argument("forAll", OptionInputType(StringType))
  }

  object Dirs {
    val HttpGet = Directive("httpGet",
      arguments = Args.Url :: Args.Headers :: Args.DelegateHeaders :: Args.QueryParams :: Args.ForAll :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))
  }
}
