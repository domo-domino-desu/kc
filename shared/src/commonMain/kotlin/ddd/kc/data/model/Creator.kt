package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Creator(
    val id: String,
    val name: String = "",
    val service: String,
    @Serializable(with = FlexibleLongSerializer::class) val indexed: Long = 0L,
    @Serializable(with = FlexibleLongSerializer::class) val updated: Long = 0L,
    val favorited: Int = 0,
    @SerialName("public_id") val publicId: String? = null,
)

fun Creator.thumbnailUrl(baseUrl: String): String = "$baseUrl/icons/${service}/${id}"

fun Creator.bannerUrl(baseUrl: String): String = "$baseUrl/banners/${service}/${id}"
