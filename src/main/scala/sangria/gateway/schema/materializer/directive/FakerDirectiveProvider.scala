package sangria.gateway.schema.materializer.directive

import java.time.{ZoneId, ZonedDateTime}
import java.util.concurrent.TimeUnit
import java.util.Random

import com.github.javafaker.Faker
import sangria.gateway.schema.CustomScalars
import sangria.gateway.schema.materializer.GatewayContext
import sangria.renderer.SchemaRenderer
import sangria.schema._

class FakerDirectiveProvider extends DirectiveProvider {
  import FakerDirectiveProvider._

  def resolvers(ctx: GatewayContext) = Seq(
    AdditionalDirectives(Seq(Dirs.FakeConfig)),
    DirectiveResolver(Dirs.Fake, c ⇒ c.withArgs(Args.Expr, Args.MinElems, Args.MaxElems, Args.Past, Args.Future) { (expr, min, max, past, future) ⇒
      FakeValue(expr, min, max, past, future, c.ctx.ctx.rnd.get, c.ctx.ctx.faker.get).coerce(c.ctx)
    })
  )

  override def anyResolver = Some({
    case c if c.value.isInstanceOf[FakeValue] ⇒ c.value.asInstanceOf[FakeValue].coerce(c)
  })
}

object FakerDirectiveProvider {
  object Args {
    val Expr = Argument("expr", OptionInputType(StringType))
    val Locale = Argument("locale", OptionInputType(StringType))
    val Seed = Argument("seed", OptionInputType(IntType))
    val MinElems = Argument("min", OptionInputType(IntType))
    val MaxElems = Argument("max", OptionInputType(IntType))
    val Past = Argument("past", OptionInputType(BooleanType))
    val Future = Argument("future", OptionInputType(BooleanType))
  }

  object Dirs {
    val Fake = Directive("fake",
      arguments = Args.Expr :: Args.MinElems :: Args.MaxElems :: Args.Past :: Args.Future :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val FakeConfig = Directive("fakeConfig",
      arguments = Args.Locale :: Args.Seed :: Nil,
      locations = Set(DirectiveLocation.Schema))
  }
}

case class FakeValue(expr: Option[String], min: Option[Int], max: Option[Int], past: Option[Boolean], future: Option[Boolean], rnd: Random, faker: Faker) {
  private lazy val withoutExpr = copy(expr = None, min = None, max = None)

  def coerce(ctx: Context[GatewayContext, _]): Action[GatewayContext, Any] = {
    def coerceType(tpe: OutputType[_]): Any = tpe match {
      case OptionType(ofType) ⇒
        if (rnd.nextBoolean()) None
        else coerceType(ofType)

      case ListType(ofType) ⇒
        val size = min.getOrElse(0) + rnd.nextInt(max.fold(20)(max ⇒ max - min.getOrElse(0) + 1))

        (1 to size).toVector.map(_ ⇒ coerceType(ofType))

      case s: ScalarType[_] if s.name == StringType.name ⇒
        expr match {
          case Some(e) ⇒ faker.expression(e)
          case None ⇒ faker.lorem().paragraph()
        }

      case s: ScalarType[_] if s.name == IntType.name ⇒
        expr match {
          case Some(e) ⇒ faker.expression(e).toInt
          case None ⇒ rnd.nextInt()
        }

      case s: ScalarType[_] if s.name == LongType.name ⇒
        expr match {
          case Some(e) ⇒ faker.expression(e).toLong
          case None ⇒ rnd.nextLong()
        }

      case s: ScalarType[_] if s.name == BigIntType.name ⇒
        expr match {
          case Some(e) ⇒ BigInt(faker.expression(e))
          case None ⇒ BigInt(rnd.nextLong())
        }

      case s: ScalarType[_] if s.name == BigDecimalType.name ⇒
        expr match {
          case Some(e) ⇒ BigDecimal(faker.expression(e))
          case None ⇒ BigDecimal(rnd.nextDouble())
        }

      case s: ScalarType[_] if s.name == IDType.name ⇒
        expr match {
          case Some(e) ⇒ faker.expression(e)
          case None ⇒ "" + rnd.nextLong()
        }

      case s: ScalarType[_] if s.name == FloatType.name ⇒
        expr match {
          case Some(e) ⇒ faker.expression(e).toDouble
          case None ⇒ rnd.nextDouble()
        }

      case s: ScalarType[_] if s.name == BooleanType.name ⇒
        expr match {
          case Some(e) ⇒ faker.expression(e).toBoolean
          case None ⇒ rnd.nextBoolean()
        }

      case s: ScalarType[_] if s.name == CustomScalars.DateTimeType.name ⇒
        resolveDate
        
      case s: CompositeType[_] ⇒
        withoutExpr

      case t ⇒
        throw new IllegalStateException(s"Can't fake type ${SchemaRenderer.renderTypeName(t)} just yet.")
    }

    coerceType(ctx.field.fieldType)
  }

  def resolveDate = {
    val isPast =
      (past, future) match {
        case (Some(p), Some(f)) ⇒ p
        case (Some(p), None) ⇒ p
        case (None, Some(f)) ⇒ !f
        case (None, None) ⇒ rnd.nextBoolean()
      }

    val date =
      if (isPast)
        faker.date().past(700, TimeUnit.DAYS)
      else
        faker.date().future(700, TimeUnit.DAYS)

    ZonedDateTime.ofInstant(date.toInstant, ZoneId.of("UTC"))
  }
}






