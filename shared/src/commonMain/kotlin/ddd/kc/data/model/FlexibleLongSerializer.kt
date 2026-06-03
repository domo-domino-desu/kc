package ddd.kc.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

object FlexibleLongSerializer : KSerializer<Long> {
  override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

  override fun deserialize(decoder: Decoder): Long {
    val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
    val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0L
    return primitive.longOrNull ?: primitive.content.toLongOrNull() ?: 0L
  }

  override fun serialize(encoder: Encoder, value: Long) {
    encoder.encodeLong(value)
  }
}
