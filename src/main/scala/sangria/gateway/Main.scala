package sangria.gateway

import com.typesafe.config.ConfigFactory
import sangria.gateway.http.GatewayServer

object Main extends App {
  val config = AppConfig.load(ConfigFactory.load())
  val server = new GatewayServer

  server.startup(config)
}
