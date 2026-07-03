package ddd.kc.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class Post(
    val id: String,
    val user: String,
    val service: String,
    @SerialName("artist_id") val artistId: String? = null,
    val title: String = "",
    @SerialName("fav_count") val favoriteCount: Int? = null,
    val content: String? = null,
    val published: String? = null,
    val added: String? = null,
    val edited: String? = null,
    val file: PostFile? = null,
    val attachments: List<PostFile> = emptyList(),
    val attachmentCount: Int? = null,
    @SerialName("shared_file") val sharedFile: Boolean = false,
    @SerialName("embed") val embed: PostEmbed? = null,
    val poll: PostPoll? = null,
    val captions: List<PostCaption>? = null,
    @Serializable(with = FlexibleTagsSerializer::class) val tags: List<String>? = null,
) : PlatformSerializable

val Post.creatorId: String
  get() = artistId ?: user

@Serializable
data class PostEmbed(
    val url: String? = null,
    val subject: String? = null,
    val description: String? = null,
) : PlatformSerializable

@Serializable
data class PostPoll(
    val options: List<PostPollOption> = emptyList(),
) : PlatformSerializable

@Serializable data class PostPollOption(val text: String = "") : PlatformSerializable

@Serializable
data class PostCaption(
    val language: String? = null,
    val content: String? = null,
) : PlatformSerializable

fun Post.allFiles(): List<PostFile> = buildList {
  file?.takeIf { it.hasPath() }?.let { add(it) }
  addAll(attachments.filter { it.hasPath() })
}

fun Post.imageFiles(): List<PostFile> = allFiles().filter { it.isImage() }

fun Post.thumbnailUrl(cdnUrl: String): String? = imageFiles().firstOrNull()?.thumbnailUrl(cdnUrl)

@OptIn(ExperimentalSerializationApi::class)
object FlexibleTagsSerializer : KSerializer<List<String>?> {
  private val delegate = ListSerializer(String.serializer())
  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun deserialize(decoder: Decoder): List<String>? {
    val jsonDecoder = decoder as? JsonDecoder
    if (jsonDecoder == null) return decoder.decodeNullableSerializableValue(delegate)
    return when (val element = jsonDecoder.decodeJsonElement()) {
      JsonNull -> null
      is JsonArray -> jsonDecoder.json.decodeFromJsonElement(delegate, element)
      is JsonPrimitive -> element.content.parsePawchiveTags()
      else -> null
    }
  }

  override fun serialize(encoder: Encoder, value: List<String>?) {
    val jsonEncoder = encoder as? JsonEncoder
    if (value == null) {
      jsonEncoder?.encodeJsonElement(JsonNull)
          ?: error("FlexibleTagsSerializer requires JSON encoding")
    } else {
      if (jsonEncoder == null) {
        encoder.encodeSerializableValue(delegate, value)
      } else {
        jsonEncoder.encodeJsonElement(jsonEncoder.json.encodeToJsonElement(delegate, value))
      }
    }
  }
}

private fun String.parsePawchiveTags(): List<String> {
  val source = trim().removeSurrounding("{", "}")
  if (source.isBlank()) return emptyList()
  val result = mutableListOf<String>()
  val current = StringBuilder()
  var quoted = false
  var escaping = false
  source.forEach { char ->
    when {
      escaping -> {
        current.append(char)
        escaping = false
      }
      char == '\\' && quoted -> escaping = true
      char == '"' -> quoted = !quoted
      char == ',' && !quoted -> {
        current.toString().trim().trim('"').takeIf { it.isNotBlank() }?.let(result::add)
        current.clear()
      }
      else -> current.append(char)
    }
  }
  current.toString().trim().trim('"').takeIf { it.isNotBlank() }?.let(result::add)
  return result
}
