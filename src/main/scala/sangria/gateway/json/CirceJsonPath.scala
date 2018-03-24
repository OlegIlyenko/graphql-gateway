package sangria.gateway.json

import com.jayway.jsonpath.{Configuration, JsonPath}
import io.circe.Json

object CirceJsonPath {
  private lazy val config =
    Configuration.builder()
      .jsonProvider(new CirceJsonProvider)
      .mappingProvider(new CirceMappingProvider)
      .build()

  def query(json: Json, jsonPath: String): Json =
    JsonPathValueWrapper.toJson(JsonPath.using(config).parse(json).read(jsonPath))
}
