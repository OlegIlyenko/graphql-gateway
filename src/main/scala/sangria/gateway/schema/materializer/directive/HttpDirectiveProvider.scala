package sangria.gateway.schema.materializer.directive

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
      }))
}

object HttpDirectiveProvider {
  object Args {
    val HeaderType = InputObjectType("Header", fields = List(
      InputField("name", StringType),
      InputField("value", StringType)))

    val QueryParamType = InputObjectType("QueryParam", fields = List(
      InputField("name", StringType),
      InputField("value", StringType)))

    val Url = Argument("url", StringType)
    val Headers = Argument("headers", OptionInputType(ListInputType(HeaderType)))
    val QueryParams = Argument("query", OptionInputType(ListInputType(QueryParamType)))
    val ForAll = Argument("forAll", OptionInputType(StringType))
  }

  object Dirs {
    val HttpGet = Directive("httpGet",
      arguments = Args.Url :: Args.Headers :: Args.QueryParams :: Args.ForAll :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))
  }
}
