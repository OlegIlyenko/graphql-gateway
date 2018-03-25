package sangria.gateway.schema

import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime, ZoneOffset, ZonedDateTime}

import sangria.schema._
import sangria.ast
import sangria.validation.ValueCoercionViolation

import scala.util.{Failure, Success, Try}

object CustomScalars {
  implicit val DateTimeType = ScalarType[ZonedDateTime]("DateTime",
    description = Some("DateTime is a scalar value that represents an ISO8601 formatted date and time."),
    coerceOutput = (date, _) ⇒ DateTimeFormatter.ISO_INSTANT.format(date),
    coerceUserInput = {
      case s: String ⇒ parseDateTime(s) match {
        case Success(date) ⇒ Right(date)
        case Failure(_) ⇒ Left(DateCoercionViolation)
      }
      case _ ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case ast.StringValue(s, _, _, _, _) ⇒ parseDateTime(s) match {
        case Success(date) ⇒ Right(date)
        case Failure(_) ⇒ Left(DateCoercionViolation)
      }
      case _ ⇒ Left(DateCoercionViolation)
    })

  def parseDateTime(s: String) = Try(DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(s).asInstanceOf[ZonedDateTime])

  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")
}
