package sangria.gateway.util

import com.typesafe.scalalogging.Logger

trait Logging {
  val logger = Logger(this.getClass)
}
