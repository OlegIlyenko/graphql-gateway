package sangria.gateway.http.client

import akka.util.ByteString
import io.circe.parser._
import play.api.libs.ws.{BodyWritable, InMemoryBody, StandaloneWSClient, WSAuthScheme}
import sangria.gateway.http.client.HttpClient.Method
import sangria.gateway.util.Logging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class PlayHttpClient(ws: StandaloneWSClient)(implicit ec: ExecutionContext) extends play.api.libs.ws.DefaultBodyWritables with HttpClient with Logging {
  private[this] val json = "application/json"

  override def request(
    method: Method.Value, url: String,
    queryParams: Seq[(String, String)], headers: Seq[(String, String)],
    body: Option[(String, String)]
  ) = {
    val m = mapMethod(method)
    val baseRequest = ws.url(url).withRequestTimeout(10.minutes).withMethod(m)
    val withParams = baseRequest.withQueryStringParameters(queryParams: _*).withHttpHeaders(headers: _*)
    val withBody = body match {
      case Some((t, content)) if t == json ⇒ withParams.withBody(parse(content).right.get)(BodyWritable(s ⇒  InMemoryBody(ByteString(s.spaces2)), t))
      case Some((t, _)) ⇒ throw new IllegalStateException(s"Unhandled body type [$t].")
      case None ⇒ withParams
    }
    val finalRequest = withBody

    logger.debug(s"Http request: $m $url")

    finalRequest.execute().map(rsp ⇒ new HttpClient.HttpResponse {
      override def statusCode = rsp.status
      override def isSuccessful = rsp.status >= 200 && rsp.status < 300
      override def asString = Future.successful(rsp.body)
      override def debugInfo = s"$method $url"
    })
  }

  override def oauthClientCredentials(url: String, clientId: String, clientSecret: String, scopes: Seq[String]): Future[HttpClient.HttpResponse] = {
    val request =
      ws.url(url)
        .withMethod("POST")
        .withAuth(clientId, clientSecret, WSAuthScheme.BASIC)
        .withBody(Map("grant_type" → Seq("client_credentials"), "scope" → scopes))

    logger.debug(s"HTTP OAuth client credentials request: $url")

    request.execute().map(rsp ⇒ new HttpClient.HttpResponse {
      override def statusCode = rsp.status
      override def isSuccessful = rsp.status >= 200 && rsp.status < 300
      override def asString = Future.successful(rsp.body)
      override def debugInfo = s"POST $url"
    })
  }

  def mapMethod(method: Method.Value) = method match {
    case Method.Get ⇒ "GET"
    case Method.Post ⇒ "POST"
  }
}
