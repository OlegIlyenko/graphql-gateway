package sangria.gateway.schema.materializer.directive

import io.circe.Json
import sangria.gateway.json.CirceJsonPath
import sangria.gateway.schema.CustomScalars
import sangria.gateway.schema.materializer.GatewayContext
import sangria.gateway.schema.materializer.GatewayContext._
import sangria.schema.ResolverBasedAstSchemaBuilder.{extractFieldValue, extractValue}
import sangria.schema._
import sangria.marshalling.circe._

import scala.concurrent.ExecutionContext

class BasicDirectiveProvider(implicit ec: ExecutionContext) extends DirectiveProvider {
  import BasicDirectiveProvider._

  def resolvers(ctx: GatewayContext) = Seq(
    AdditionalTypes(CustomScalars.DateTimeType),

    DirectiveResolver(Dirs.Context, c ⇒ c.withArgs(Args.NameOpt, Args.Path) { (name, path) ⇒
      name
        .map(n ⇒ extractValue(c.ctx.field.fieldType, c.ctx.ctx.vars.get(n)))
        .orElse(path.map(p ⇒ extractValue(c.ctx.field.fieldType, Some(CirceJsonPath.query(c.ctx.ctx.vars, p)))))
        .getOrElse(throw SchemaMaterializationException(s"Can't find a directive argument 'path' or 'name'."))
    }),

    DirectiveResolver(Dirs.Value, c ⇒ c.withArgs(Args.NameOpt, Args.Path) { (name, path) ⇒
      def extract(value: Any) =
        name
          .map(n ⇒ extractValue(c.ctx.field.fieldType, value.asInstanceOf[Json].get(n)))
          .orElse(path.map(p ⇒ extractValue(c.ctx.field.fieldType, Some(CirceJsonPath.query(value.asInstanceOf[Json], p)))))
          .getOrElse(throw SchemaMaterializationException(s"Can't find a directive argument 'path' or 'name'."))

      c.lastValue map (_.map(extract)) getOrElse extract(c.ctx.value)
    }),

    DirectiveResolver(Dirs.Arg, c ⇒
      extractValue(c.ctx.field.fieldType,
        convertArgs(c.ctx.args, c.ctx.astFields.head).get(c arg Args.NameReq))),

    DynamicDirectiveResolver[GatewayContext, Json]("const", c ⇒
      extractValue(c.ctx.field.fieldType, Some(c.args.get("value") match {
        case Some(json: Json) if json.isString ⇒ Json.fromString(c.ctx.ctx.fillPlaceholders(c.ctx, json.asString.get))
        case Some(jv) ⇒ jv
        case _ ⇒ Json.Null
      }))),

    DirectiveResolver(Dirs.JsonConst, c ⇒
      extractValue(c.ctx.field.fieldType,
        Some(io.circe.parser.parse(c.ctx.ctx.fillPlaceholders(c.ctx, c arg Args.JsonValue)).fold(throw _, identity)))))

  override val anyResolver = Some({
    case c if c.value.isInstanceOf[Json] ⇒ ResolverBasedAstSchemaBuilder.extractFieldValue[GatewayContext, Json](c)
  })
}

object BasicDirectiveProvider {
  object Args {
    val NameOpt = Argument("name", OptionInputType(StringType))
    val NameReq = Argument("name", StringType)
    val Path = Argument("path", OptionInputType(StringType))
    val JsonValue = Argument("value", StringType)
  }

  object Dirs {
    val Context = Directive("context",
      arguments = Args.NameOpt :: Args.Path :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val Value = Directive("value",
      arguments = Args.NameOpt :: Args.Path :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val Arg = Directive("arg",
      arguments = Args.NameReq :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition))

    val JsonConst = Directive("jsonValue",
      arguments = Args.JsonValue :: Nil,
      locations = Set(DirectiveLocation.FieldDefinition, DirectiveLocation.Schema))
  }
}




