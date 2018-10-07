package sangria.gateway.http.client

import scala.concurrent.Future

trait HttpClient {
  import HttpClient._

  def request(
    method: Method.Value,
    url: String,
    queryParams: Seq[(String, String)] = Seq.empty,
    headers: Seq[(String, String)] = Seq.empty,
    body: Option[(String, String)] = None): Future[HttpResponse]

  def oauthClientCredentials(
    url: String,
    clientId: String,
    clientSecret: String,
    scopes: Seq[String]): Future[HttpResponse]
}

object HttpClient {
  object Method extends Enumeration {
    val Get, Post = Value
  }

  trait HttpResponse {
    def statusCode: Int
    def isSuccessful: Boolean
    def asString: Future[String]
    def debugInfo: String
  }
}