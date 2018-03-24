package sangria.gateway.json

import java.io.InputStream

import com.jayway.jsonpath.{InvalidJsonException, JsonPathException}
import com.jayway.jsonpath.spi.json.JsonProvider
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

import scala.io.Source

class CirceJsonProvider extends JsonProvider {
  override def createArray(): AnyRef = JsonPathValueWrapper.emptyArray

  override def setArrayIndex(array: Any, idx: Int, newValue: Any): Unit =
    array match {
      case arr: java.util.ArrayList[Any] @unchecked ⇒ if (idx == arr.size) arr.add(newValue) else arr.set(idx, newValue)
      case _ ⇒ error("setArrayIndex is only available on new objects")
    }

  override def length(obj: Any): Int = {
    obj match {
      case json: Json if json.isArray ⇒
        json.asArray.get.size
      case json: Json if json.isObject ⇒
        json.asObject.get.size
      case json: Json if json.isString ⇒
        json.asString.get.length
      case arr: java.util.ArrayList[_] ⇒
        arr.size
      case obj: java.util.LinkedHashMap[_, _] ⇒
        obj.size
      case s: String ⇒
        s.length
      case _ ⇒ throw new JsonPathException(s"Length operation cannot be applied to ${if (obj != null) obj.getClass.getName else "null"}")
    }
  }

  override def getArrayIndex(obj: Any, idx: Int): AnyRef = obj match {
    case arr: java.util.ArrayList[AnyRef @unchecked] ⇒ arr.get(idx)
    case json: Json if json.isArray ⇒ json.asArray.get(idx)
    case o ⇒ notJson(o)
  }

  override def getArrayIndex(obj: Any, idx: Int, unwrap: Boolean): AnyRef =
    getArrayIndex(obj, idx)

  override def createMap(): AnyRef = JsonPathValueWrapper.emptyMap
  override def setProperty(obj: Any, key: Any, value: Any): Unit = obj match {
    case obj: java.util.LinkedHashMap[String @unchecked, Any @unchecked] ⇒ obj.put(key.asInstanceOf[String], value)
    case _ ⇒ error("setProperty is only available on new objects")
  }
  override def getPropertyKeys(obj: Any) =
    obj match {
      case obj: java.util.LinkedHashMap[String, Any] @unchecked ⇒ obj.keySet
      case obj: Json if obj.isObject ⇒ obj.asObject.get.keys.asJavaCollection
      case o ⇒ Vector.empty.asJavaCollection
    }

  override def removeProperty(obj: Any, key: Any): Unit =
    obj match {
      case obj: java.util.LinkedHashMap[_, _] ⇒ obj.remove(key)
      case _ ⇒ error("removeProperty is only available on new objects")
    }

  override def getMapValue(obj: Any, key: String): AnyRef =
    obj match {
      case obj: java.util.LinkedHashMap[String, AnyRef] @unchecked if obj.containsKey(key) ⇒ obj.get(key)
      case json: Json if json.isObject && json.asObject.get.contains(key) ⇒ json.asObject.get(key).get
      case _ ⇒ JsonProvider.UNDEFINED
    }

  override def toIterable(obj: Any) =
    if (isArray(obj)) obj.asInstanceOf[Json].asArray.get.asJava
    else error(s"Cannot iterate over ${if (obj != null) obj.getClass.getName else "null"}")

  override def unwrap(obj: Any): AnyRef =
    obj match {
      case json: Json ⇒
        json.fold(
          jsonNull = null,
          jsonBoolean = b ⇒ b: java.lang.Boolean,
          jsonNumber = _.toBigDecimal.get,
          jsonString = identity,
          jsonArray = JsonPathValueWrapper.array(_),
          jsonObject = JsonPathValueWrapper.map(_))

      case obj ⇒ obj.asInstanceOf[AnyRef]
    }

  override def isMap(obj: Any): Boolean = obj match {
    case obj: java.util.HashMap[_, _] ⇒ true
    case obj: Json if obj.isObject ⇒ true
    case _ ⇒ false
  }

  override def isArray(obj: Any): Boolean = obj match {
    case obj: java.util.ArrayList[_] ⇒ true
    case obj: Json if obj.isArray ⇒ true
    case _ ⇒ false
  }

  override def toJson(obj: Any): String =
    obj match {
      case json: Json ⇒ json.spaces2
      case obj ⇒ JsonPathValueWrapper.toJson(obj).spaces2
    }

  override def parse(json: String): AnyRef =
    io.circe.parser.parse(json).fold(e ⇒ throw new InvalidJsonException(e, json), identity)

  override def parse(jsonStream: InputStream, charset: String): AnyRef = {
    val json = Source.fromInputStream(jsonStream, charset).getLines.mkString("\n")

    io.circe.parser.parse(json).fold(e ⇒ throw new InvalidJsonException(e, json), identity)
  }

  private def notJson(obj: Any) = error("Not a JSON value")
  private def error(message: String) = throw new JsonPathException(message)
}

object JsonPathValueWrapper {
  def emptyMap: java.util.LinkedHashMap[String, Any] =
    new java.util.LinkedHashMap[String, Any]

  def map(obj: JsonObject): java.util.LinkedHashMap[String, Any] = {
    val values = new java.util.LinkedHashMap[String, Any](obj.size)

    obj.keys.foreach { key ⇒
      values.put(key, obj(key).get)
    }

    values
  }

  def emptyArray = new java.util.ArrayList[Any]

  def array(obj: Vector[Any]): java.util.ArrayList[Any] = {
    val values = new java.util.ArrayList[Any](obj.size)

    obj.foreach { key ⇒
      values.add(key)
    }

    values
  }

  def toJson(obj: Any): Json = obj match {
    case arr: java.util.ArrayList[_] ⇒
      Json.arr(arr.asScala.map(toJson): _*)
    case obj: java.util.LinkedHashMap[String, Any] @unchecked ⇒
      Json.obj(obj.asScala.toSeq.map{case (k, v) ⇒ k → toJson(v)}: _*)
    case json: Json ⇒
      json
    case v: String ⇒
      Json.fromString(v)
    case v: Boolean ⇒
      Json.fromBoolean(v)
    case v: Int ⇒
      Json.fromInt(v)
    case v: Float ⇒
      Json.fromFloat(v).get
    case v: Double ⇒
      Json.fromDouble(v).get
    case v: BigInt ⇒
      Json.fromBigInt(v)
    case v: BigDecimal ⇒
      Json.fromBigDecimal(v)
    case null ⇒
      Json.Null
    case v ⇒
      throw new JsonPathException("Unsupported value: " + v)
  }
}
