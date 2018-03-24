package sangria.gateway.json

import com.jayway.jsonpath.{Configuration, TypeRef}
import com.jayway.jsonpath.spi.mapper.MappingProvider

class CirceMappingProvider extends MappingProvider {
  override def map[T](source: Any, targetType: Class[T], configuration: Configuration): T =
    throw new UnsupportedOperationException("Circe JSON provider does not support mapping!")

  override def map[T](source: Any, targetType: TypeRef[T], configuration: Configuration): T =
    throw new UnsupportedOperationException("Circe JSON provider does not support TypeRef mapping!")
}
