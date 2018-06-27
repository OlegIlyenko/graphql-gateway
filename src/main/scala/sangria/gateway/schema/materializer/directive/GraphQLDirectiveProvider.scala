package sangria.gateway.schema.materializer.directive

import io.circe.Json
import sangria.gateway.schema.materializer.{GatewayContext, GraphQLIncludedSchema}
import sangria.schema._
import sangria.ast
import sangria.ast.AstVisitor
import sangria.gateway.schema.CustomScalars
import sangria.marshalling.queryAst.queryAstInputUnmarshaller
import sangria.validation.TypeInfo
import sangria.visitor.VisitorCommand
import sangria.introspection.TypeNameMetaField
import sangria.marshalling.circe._

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class GraphQLDirectiveProvider(implicit ec: ExecutionContext) extends DirectiveProvider {
  import GraphQLDirectiveProvider._

  private val TypeNameField = ast.Field(None, TypeNameMetaField.name, Vector.empty, Vector.empty, Vector.empty)

  def resolvers(ctx: GatewayContext) = Seq(
    AdditionalDirectives(Seq(Dirs.IncludeGraphQL)),
    AdditionalTypes(ctx.allTypes.toList),

    ExistingFieldResolver {
      case (o: GraphQLIncludedSchema, _, f) if ctx.graphqlIncludes.exists(_.include.name == o.include.name) && f.astDirectives.exists(_.name == "delegate") ⇒
        val schema = ctx.graphqlIncludes.find(_.include.name == o.include.name).get

        c ⇒ {
          val (updatedFields, fragments, vars) = prepareOriginFields(o, c.query, c.schema, c.astFields, c.parentType)
          val varDefs = vars.toVector.flatMap(v ⇒ c.query.operation(c.ctx.operationName).get.variables.find(_.name == v))
          val (ot, n) = c.query.operations.map(_._2.operationType).toSeq.distinct.toList match {
            case h :: Nil => (h, Some("Delegated" + h))
            case x => throw new IllegalStateException(s"Query contains operations [${x.mkString(", ")}].")
          }
          val queryOp = ast.OperationDefinition(operationType = ot, name = n, variables = varDefs, selections = updatedFields)
          val query = ast.Document(queryOp +: fragments)

          ctx.request(schema, query, c.ctx.queryVars, c.astFields.head.outputName).map(value ⇒
            ResolverBasedAstSchemaBuilder.extractValue[Json](c.field.fieldType, Some(value)))
        }
    },

    ExistingScalarResolver {
      case ctx if
          ctx.origin.isInstanceOf[GraphQLIncludedSchema] ||
          ctx.existing.name != CustomScalars.DateTimeType.name ⇒

        ctx.existing.copy(
          coerceUserInput = Right(_),
          coerceOutput = (v, _) ⇒ v,
          coerceInput = v ⇒ Right(queryAstInputUnmarshaller.getScalaScalarValue(v)))
    },

    // Current behaviour: for all conflicting types, only one would be picked -
    // we assume that their structure and semantics are the same.
    // Potential future improvement: provide more flexible, directive-based approach to resolve the name conflicts
    ConflictResolver((origin, conflictingTypes) ⇒
      conflictingTypes
        .collectFirst{case opt: BuiltMaterializedTypeInst ⇒ opt}
        .getOrElse(conflictingTypes.head)),

    DirectiveFieldProvider(Dirs.IncludeField, _.withArgs(Args.Fields) { fields ⇒
      fields.toList.flatMap { f ⇒
        val name = f("schema").asInstanceOf[String]
        val typeName = f("type").asInstanceOf[String]
        val includes = f.get("fields").asInstanceOf[Option[Option[Seq[String]]]].flatten

        ctx.findFields(name, typeName, includes)
      }
    }))

  private def prepareOriginFields(origin: GraphQLIncludedSchema, query: ast.Document, schema: Schema[GatewayContext, _], queryFields: Vector[ast.Field], parentType: OutputType[_]) =
    queryFields.foldLeft((Vector.empty[ast.Field], Vector.empty[ast.FragmentDefinition], Set.empty[String])) {
      case ((fieldAcc, fragAcc, varAcc), f) ⇒
        val (ufield, ufrag, uvars) =  prepareAst(f, origin, query, schema, Some(parentType), fragAcc.map(_.name).toSet)

        (fieldAcc :+ ufield, fragAcc ++ ufrag, varAcc ++ uvars)
    }

  private def prepareAst[N <: ast.AstNode](
    node: N,
    origin: GraphQLIncludedSchema,
    query: ast.Document,
    schema: Schema[GatewayContext, _],
    parentType: Option[OutputType[_]],
    seenFragments: Set[String]
  ): (N, Vector[ast.FragmentDefinition], Set[String]) = {
    val fragments = mutable.HashSet[String]()
    val vars = mutable.HashSet[String]()

    val updatedNode =
      AstVisitor.visitAstWithTypeInfo(schema, node) { typeInfo ⇒
        parentType foreach typeInfo.forcePushType

        AstVisitor(
          onEnter = {
            case field: ast.Field ⇒
              typeInfo.previousParentType match {
                case Some(parent) ⇒
                  origin.schema.outputTypes.get(parent.name) match {
                    case Some(originType: ObjectLikeType[_, _]) if !originType.fieldsByName.contains(field.name) ⇒
                      // Field does not belong to an origin - no delegation
                      VisitorCommand.Delete

                    case _ ⇒ VisitorCommand.Continue
                  }

                case None ⇒ VisitorCommand.Continue
              }

            case v: ast.VariableValue ⇒
              vars += v.name
              VisitorCommand.Continue

            case f: ast.FragmentSpread ⇒
              fragments += f.name
              VisitorCommand.Continue
          },
          onLeave = {
            case field: ast.Field ⇒
              typeInfo.parentType match {
                case Some(parent) ⇒
                  origin.schema.outputTypes.get(parent.name) match {
                    case Some(originType: ObjectLikeType[_, _]) if originType.fieldsByName contains field.name ⇒
                      injectTypeName(origin, field, parent, typeInfo)

                    case _ ⇒ VisitorCommand.Continue
                  }

                case None ⇒ VisitorCommand.Continue
              }
          })
      }

    val fragmentsToUpdate = fragments.toVector.filterNot(name ⇒ seenFragments.contains(name))
    val allSeenFragments = seenFragments ++ fragmentsToUpdate
    val updatedFragments = fragmentsToUpdate.map(fn ⇒ prepareAst(query.fragments(fn), origin, query, schema, None, allSeenFragments))
    val allVars = vars.toSet ++ updatedFragments.flatMap(_._3)
    val finalFragments = updatedFragments.flatMap(u ⇒ u._1 +: u._2)

    (updatedNode, finalFragments, allVars)
  }

  def injectTypeName(origin: GraphQLIncludedSchema, field: ast.Field, parent: CompositeType[_], typeInfo: TypeInfo): VisitorCommand = {
    val fieldType =
      origin.schema.outputTypes.get(parent.name) match {
        case Some(originType: ObjectLikeType[_, _]) ⇒ originType.fieldsByName.get(field.name).map(_.head.fieldType)
        case _ ⇒ None
      }

    fieldType.map(_.namedType).fold(VisitorCommand.Continue: VisitorCommand) {
      case _: AbstractType ⇒
        // Field has abstract result type, so we need to inject `__typename` to make proper instance checks at the Gateway level
        VisitorCommand.Transform(field.copy(selections = TypeNameField +: field.selections))
      case _ ⇒ VisitorCommand.Continue
    }
  }
}

object GraphQLDirectiveProvider {
  object Args {
    val IncludeType = InputObjectType("GraphQLSchemaInclude", fields = List(
      InputField("name", StringType),
      InputField("url", StringType)))

    val IncludeFieldsType = InputObjectType("GraphQLIncludeFields", fields = List(
      InputField("schema", StringType),
      InputField("type", StringType),
      InputField("fields", OptionInputType(ListInputType(StringType)))))

    val Schemas = Argument("schemas", ListInputType(IncludeType))
    val Fields = Argument("fields", ListInputType(IncludeFieldsType))
  }

  object Dirs {
    val IncludeGraphQL = Directive("includeGraphQL",
      arguments = Args.Schemas :: Nil,
      locations = Set(DirectiveLocation.Schema))

    val IncludeField = Directive("include",
      arguments = Args.Fields :: Nil,
      locations = Set(DirectiveLocation.Object))
  }
}
