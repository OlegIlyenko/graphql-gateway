package sangria.gateway.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase
import ch.qos.logback.core.pattern.color.ANSIConstants._

class LogColors extends ForegroundCompositeConverterBase[ILoggingEvent] {
  override def getForegroundColorCode(event: ILoggingEvent): String =
    event.getLevel.toInt match {
      case Level.ERROR_INT ⇒
        BOLD + RED_FG
      case Level.WARN_INT ⇒
        YELLOW_FG
      case Level.INFO_INT ⇒
        GREEN_FG
      case Level.DEBUG_INT ⇒
        CYAN_FG
      case _ ⇒
        DEFAULT_FG
    }
}
