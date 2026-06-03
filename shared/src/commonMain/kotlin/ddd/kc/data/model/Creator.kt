package ddd.kc.data.model

import ddd.kc.ui.icons.ServiceIconCatalogRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Creator(
    val id: String = "",
    val name: String = "",
    val service: String = "",
    @Serializable(with = FlexibleLongSerializer::class) val indexed: Long = 0L,
    @Serializable(with = FlexibleLongSerializer::class) val updated: Long = 0L,
    val favorited: Int = 0,
    @SerialName("public_id") val publicId: String? = null,
)

fun Creator.thumbnailUrl(cdnUrl: String): String = "$cdnUrl/icons/${service}/${id}"

private val kemonoFallbackServices =
    listOf(
        "patreon",
        "fanbox",
        "discord",
        "fantia",
        "afdian",
        "boosty",
        "dlsite",
        "gumroad",
        "subscribestar",
    )
private val coomerFallbackServices = listOf("onlyfans", "fansly", "candfans")

fun Platform.services(): List<String> {
  val catalog = ServiceIconCatalogRepository.catalog.value
  val key = name.lowercase()
  val fromJson = catalog?.servicesForPlatform(key)
  return if (!fromJson.isNullOrEmpty()) fromJson
  else
      when (this) {
        Platform.KEMONO -> kemonoFallbackServices
        Platform.COOMER -> coomerFallbackServices
      }
}
