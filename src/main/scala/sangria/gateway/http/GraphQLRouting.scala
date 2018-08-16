package sangria.gateway.http

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{ExceptionHandler => _, _}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.{jsonMarshaller, jsonUnmarshaller}
import sangria.gateway.http.GraphQLRequestUnmarshaller.{explicitlyAccepts, _}
import akka.http.scaladsl.marshalling.{ToResponseMarshallable => TRM}
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaTypes.`text/html`
import de.heikoseeberger.akkasse.scaladsl.model._
import de.heikoseeberger.akkasse.scaladsl.marshalling.EventStreamMarshalling._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.parser._
import sangria.ast.Document
import sangria.execution._
import sangria.execution.deferred.DeferredResolver
import sangria.gateway.AppConfig
import sangria.gateway.schema.SchemaProvider
import sangria.gateway.schema.materializer.GatewayContext
import sangria.gateway.schema.materializer.directive.RequestResolver
import sangria.gateway.util.Logging
import sangria.parser.{QueryParser, SyntaxError}
import sangria.marshalling.circe._
import sangria.slowlog.SlowLog

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

class GraphQLRouting[Val](
  config: AppConfig,
  schemaProvider: SchemaProvider[GatewayContext, Val],
  deferredResolver: DeferredResolver[GatewayContext]
)(implicit ec: ExecutionContext) extends Logging {
  val route: Route =
    path("graphql") {
      get {
        (includeIf(config.graphiql) & explicitlyAccepts(`text/html`)) {
          getFromResource("assets/graphiql.html")
        } ~
        parameters('query, 'operationName.?, 'variables.?) { (query, operationName, variables) ⇒
          QueryParser.parse(query) match {
            case Success(ast) ⇒
              variables.map(parse) match {
                case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
                case Some(Right(json)) ⇒ executeGraphQL(ast, operationName, json)
                case None ⇒ executeGraphQL(ast, operationName, Json.obj())
              }
            case Failure(error) ⇒ complete(BadRequest, formatError(error))
          }
        }
      } ~
      post {
        parameters('query.?, 'operationName.?, 'variables.?) { (queryParam, operationNameParam, variablesParam) ⇒
          entity(as[Json]) { body ⇒
            val query = queryParam orElse root.query.string.getOption(body)
            val operationName = operationNameParam orElse root.operationName.string.getOption(body)
            val variablesStr = variablesParam orElse root.variables.string.getOption(body)

            query.map(QueryParser.parse(_)) match {
              case Some(Success(ast)) ⇒
                variablesStr.map(parse) match {
                  case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
                  case Some(Right(json)) ⇒ executeGraphQL(ast, operationName, json)
                  case None ⇒ executeGraphQL(ast, operationName, root.variables.json.getOption(body) getOrElse Json.obj())
                }
              case Some(Failure(error)) ⇒ complete(BadRequest, formatError(error))
              case None ⇒ complete(BadRequest, formatError("No query to execute"))
            }
          } ~
          entity(as[Document]) { document ⇒
            variablesParam.map(parse) match {
              case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
              case Some(Right(json)) ⇒ executeGraphQL(document, operationNameParam, json)
              case None ⇒ executeGraphQL(document, operationNameParam, Json.obj())
            }
          }
        }
      }
    } ~
    (get & path("schema-updates")) {
      schemaProvider.schemaChanges match {
        case Some(source) ⇒
          complete(source.filter(identity).map(_ ⇒ ServerSentEvent("changed")))
        case None ⇒
          complete(200 → "Schema reloading is disabled")
      }
    } ~
    (get & path("schema.json")) {
      complete(schemaProvider.schemaInfo.map {
        case Some(info) ⇒ TRM(OK → info.schemaIntrospection)
        case None ⇒ noSchema
      })
    } ~
    (get & path("schema.graphql")) {
      complete(schemaProvider.schemaInfo.map {
        case Some(info) ⇒ TRM(OK → HttpEntity(`application/graphql`, info.schemaRendered))
        case None ⇒ noSchema
      })
    } ~
    (get & pathEndOrSingleSlash) {
      redirect("/graphql", PermanentRedirect)
    }

  private val reducers = {
    val complexityRejector = QueryReducer.rejectComplexQueries(config.limit.complexity, (complexity: Double, _: Any) ⇒
      TooComplexQueryError(s"Query complexity is $complexity but max allowed complexity is ${config.limit.complexity}. Please reduce the number of the fields in the query."))

    val depthRejector = QueryReducer.rejectMaxDepth(config.limit.maxDepth)

    val baseReducers =
      if (config.limit.allowIntrospection)
        Nil
      else
        QueryReducer.rejectIntrospection(includeTypeName = false) :: Nil

    baseReducers ++ List(complexityRejector, depthRejector)
  }

  private val middleware = {
    val withSlowLog =
      if (config.slowLog.enabled)
        SlowLog(logger.underlying, config.slowLog.threshold, config.slowLog.extension) :: Nil
      else
        Nil

    if (config.slowLog.apolloTracing)
      SlowLog.apolloTracing :: withSlowLog
    else
      withSlowLog
  }

  def executeGraphQL(query: Document, operationName: Option[String], variables: Json) =
    complete(schemaProvider.schemaInfo.flatMap {
      case Some(schemaInfo) ⇒
        val actualVariables = if (variables.isNull) Json.obj() else variables

        Executor.execute(schemaInfo.schema, query, schemaInfo.ctx.copy(operationName = operationName, queryVars = actualVariables),
          root = schemaInfo.value,
          variables = actualVariables,
          operationName = operationName,
          queryReducers = reducers.asInstanceOf[List[QueryReducer[GatewayContext, _]]],
          middleware = middleware ++ schemaInfo.middleware,
          exceptionHandler = exceptionHandler,
          deferredResolver = deferredResolver)
            .map(res ⇒ TRM(OK → res))
            .recover {
              case error: QueryAnalysisError ⇒ TRM(BadRequest → error.resolveError)
              case error: ErrorWithResolver ⇒ TRM(InternalServerError → error.resolveError)
            }
      case None ⇒ Future.successful(noSchema)
    })

  def formatError(error: Throwable): Json = error match {
    case syntaxError: SyntaxError ⇒
      Json.obj("errors" → Json.arr(
      Json.obj(
        "message" → Json.fromString(syntaxError.getMessage),
        "locations" → Json.arr(Json.obj(
          "line" → Json.fromBigInt(syntaxError.originalError.position.line),
          "column" → Json.fromBigInt(syntaxError.originalError.position.column))))))
    case NonFatal(e) ⇒
      formatError(e.getMessage)
    case e ⇒
      throw e
  }

  def formatError(message: String): Json =
    Json.obj("errors" → Json.arr(Json.obj("message" → Json.fromString(message))))

  def noSchema = TRM(InternalServerError → "No schema defined")

  val exceptionHandler = ExceptionHandler {
    case (m, error: TooComplexQueryError) ⇒ HandledException(error.getMessage)
    case (m, QueryReducingError(error: MaxQueryDepthReachedError, _)) ⇒ HandledException(error.getMessage)
    case (m, NonFatal(error)) ⇒
      logger.error("Error during GraphQL query execution", error)
      HandledException(error.getMessage)
  }

  case class TooComplexQueryError(message: String) extends Exception(message)
}
