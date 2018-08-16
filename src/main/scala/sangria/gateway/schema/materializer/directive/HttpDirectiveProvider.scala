package sangria.gateway.schema.materializer.directive

import io.circe.Json
import sangria.execution.deferred.{Deferred, DeferredResolver}
import sangria.gateway.http.client.HttpClient
import sangria.gateway.json.CirceJsonPath
import sangria.gateway.schema.materializer.GatewayContext
import sangria.gateway.schema.materializer.GatewayContext.{convertArgs, namedType}
import sangria.schema.ResolverBasedAstSchemaBuilder.extractValue
import sangria.schema._
import sangria.marshalling.circe._
import sangria.schema.InputObjectType.DefaultInput

import scala.concurrent.{ExecutionContext, Future}

class HttpDirectiveProvider extends DirectiveProvider {
  import HttpDirectiveProvider._

  def resolvers(ctx: GatewayContext) = Seq(
    DirectiveResolver(Dirs.HttpGet,
      complexity = Some(_ ⇒ (_, _, _) ⇒ 1000.0),
      resolve = c ⇒ {
        val json = c.ctx.value match {
          case j: Json => j
          case _ => Json.Null
        }
        RequestDescription(
          c.ctx,
          c.arg(Args.Url),
          c.arg(Args.Headers),
          c.arg(Args.QueryParams),
          c.arg(Args.ForAll),
          c.arg(Args.BatchUrl),
          c.arg(Args.IdFieldInBatchResponse),
          json
        )
      }
    )
  )
}

case class RequestDescription(
  context: Context[GatewayContext, _],
  rawUrl: String,
  rawHeaders: Option[Seq[DefaultInput]],
  rawQueryParams: Option[Seq[DefaultInput]],
  forAll: Option[String],
  batchUrl: Option[String],
  idFieldInBatchResponse: Option[String],
  id: Json
) extends Deferred[Json]

class RequestResolver(client: HttpClient) extends DeferredResolver[GatewayContext] {
  def resolve(deferred: Vector[Deferred[Any]], gwCtx: GatewayContext, queryState: Any)(implicit ec: ExecutionContext): Vector[Future[Any]] = {
    deferred.map {
      case rd: RequestDescription =>
        import rd._
        val args = Some(convertArgs(context.args, context.astFields.head))

        def extractMap(in: Option[scala.Seq[InputObjectType.DefaultInput]], elem: Json) =
          rawHeaders.map(_.map(h ⇒ h("name").asInstanceOf[String] → gwCtx.fillPlaceholders(context, h("value").asInstanceOf[String], args, elem))).getOrElse(Nil)

        def makeRequest(tpe: OutputType[_], c: Context[GatewayContext, _], args: Option[Json], elem: Json = Json.Null): Future[Json] = {
          val url = gwCtx.fillPlaceholders(c, rawUrl, args, elem)
          val headers = extractMap(rawHeaders, elem)
          val query = extractMap(rawQueryParams, elem)

          client.request(HttpClient.Method.Get, url, query, headers).flatMap(GatewayContext.parseJson)
        }

        val fieldType: OutputType[_] = context.field.fieldType
        forAll match {
          case Some(jsonPath) ⇒
            CirceJsonPath.query(id, jsonPath) match {
              case json: Json if json.isArray ⇒
                Future.sequence(json.asArray.get.map(e ⇒ makeRequest(namedType(fieldType), context, args, e))) map { v ⇒
                  extractValue(fieldType, Some(Json.arr(v.asInstanceOf[Seq[Json]]: _*)))
                }
              case e ⇒
                makeRequest(fieldType, context, args, e)
            }
          case None ⇒
            makeRequest(fieldType, context, args)
        }
    }
  }
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
    val BatchUrl = Argument("batchUrl", OptionInputType(StringType))
    val IdFieldInBatchResponse = Argument("idFieldInBatchResponse", OptionInputType(StringType))
  }

  object Dirs {
    val HttpGet = Directive("httpGet",
      arguments = Args.Url :: Args.Headers :: Args.QueryParams :: Args.ForAll :: Args.BatchUrl :: Args.IdFieldInBatchResponse :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))
  }
}
