package sangria.gateway.http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.ByteString
import sangria.gateway.util.Logging

import scala.concurrent.{ExecutionContext, Future}

class AkkaHttpClient(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends HttpClient with Logging {
  import AkkaHttpClient._
  import HttpClient._

  override def request(method: Method.Value, url: String, queryParams: Seq[(String, String)] = Seq.empty, headers: Seq[(String, String)] = Seq.empty, body: Option[(String, String)] = None) = {
    val m = mapMethod(method)
    val query = Query(queryParams: _*)
    val hs = headers.map(header)
    val uri = Uri(url).withQuery(query)
    val entity = body.fold(HttpEntity.Empty){case (tpe, content) ⇒ HttpEntity(contentType(tpe), ByteString(content))}
    val request = HttpRequest(m, uri, hs.toVector, entity)
    val client = Http().singleRequest(_: HttpRequest)
    val richClient = RichHttpClient.httpClientWithRedirect(client)

    logger.debug(s"Http request: ${m.value} $url")

    richClient(request).map(AkkaHttpResponse(m, url, _))
  }

  override def oauthClientCredentials(url: String, clientId: String, clientSecret: String, scopes: Seq[String]): Future[HttpResponse] =
    throw new IllegalStateException("Not yet implemented, please use play implementation.")

  private def contentType(str: String) = ContentType.parse(str).fold(
    errors ⇒ throw ClientError(s"Invalid content type '$str'", errors.map(_.detail)),
    identity)

  private def header(nameValue: (String, String)) = HttpHeader.parse(nameValue._1, nameValue._2) match {
    case ParsingResult.Ok(_, errors) if errors.nonEmpty ⇒ throw ClientError(s"Invalid header '${nameValue._1}'", errors.map(_.detail))
    case ParsingResult.Error(error) ⇒ throw ClientError(s"Invalid header '${nameValue._1}'", Seq(error.detail))
    case ParsingResult.Ok(h, _) ⇒ h
  }

  def mapMethod(method: Method.Value) = method match {
    case Method.Get ⇒ HttpMethods.GET
    case Method.Post ⇒ HttpMethods.POST
  }

  object RichHttpClient {
    import akka.http.scaladsl.model.HttpResponse
    type HttpClient = HttpRequest ⇒ Future[HttpResponse]

    def redirectOrResult(client: HttpClient)(response: HttpResponse): Future[HttpResponse] =
      response.status match {
        case StatusCodes.Found | StatusCodes.MovedPermanently | StatusCodes.SeeOther ⇒
          val newUri = response.header[Location].get.uri
          // Always make sure you consume the response entity streams (of type Source[ByteString,Unit]) by for example connecting it to a Sink (for example response.discardEntityBytes() if you don’t care about the response entity), since otherwise Akka HTTP (and the underlying Streams infrastructure) will understand the lack of entity consumption as a back-pressure signal and stop reading from the underlying TCP connection!
          response.discardEntityBytes()

          logger.debug(s"Http redirect: ${HttpMethods.GET.value} $newUri")

          client(HttpRequest(method = HttpMethods.GET, uri = newUri))

        case _ ⇒ Future.successful(response)
      }

    def httpClientWithRedirect(client: HttpClient): HttpClient = {
      lazy val redirectingClient: HttpClient =
        req ⇒ client(req).flatMap(redirectOrResult(redirectingClient)) // recurse to support multiple redirects

      redirectingClient
    }
  }

  case class ClientError(message: String, errors: Seq[String]) extends Exception(message + ":\n" + errors.map("  * "  + _).mkString("\n"))
}

object AkkaHttpClient {
  case class AkkaHttpResponse(method: HttpMethod, url: String, response: HttpResponse)(implicit mat: Materializer) extends HttpClient.HttpResponse {
    def asString = Unmarshal(response).to[String]
    def statusCode = response.status.intValue()
    def isSuccessful = response.status.isSuccess()
    def debugInfo = s"${method.value} $url"
  }
}

