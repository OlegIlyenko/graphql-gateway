package sangria.gateway.http.client

import io.circe.parser._
import play.api.libs.ws.StandaloneWSClient
import sangria.gateway.http.client.HttpClient.Method

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class PlayHttpClient(ws: StandaloneWSClient)(implicit ec: ExecutionContext) extends play.api.libs.ws.DefaultBodyWritables with HttpClient {
  private[this] val json = "application/json"

  override def request(
    method: Method.Value, url: String,
    queryParams: Seq[(String, String)], headers: Seq[(String, String)],
    body: Option[(String, String)]
  ) = {
    val baseRequest = ws.url(url).withRequestTimeout(10.minutes).withMethod(method.toString.toUpperCase)
    val withParams = baseRequest.withQueryStringParameters(queryParams: _*).withHttpHeaders(headers: _*)
    val withBody = body match {
      case Some((t, content)) if t == json => withParams.withBody(parse(content).right.get.spaces2)
      case Some((t, _)) => throw new IllegalStateException(s"Unhandled body type [$t].")
      case None => withParams
    }
    val finalRequest = withBody
    finalRequest.execute().map(rsp => new HttpClient.HttpResponse {
      override def statusCode = rsp.status
      override def isSuccessful = rsp.status >= 200 && rsp.status < 300
      override def asString = Future.successful(rsp.body)
      override def debugInfo = s"$method $url"
    })
  }
}
