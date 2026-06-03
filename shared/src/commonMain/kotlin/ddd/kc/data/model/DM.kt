package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DM(
    val id: String? = null,
    val service: String? = null,
    val user: String? = null,
    val content: String? = null,
    val added: String? = null,
    @SerialName("hash") val hash: String? = null,
    val file: PostFile? = null,
    val embeds: List<PostEmbed>? = null,
    val artist: Creator? = null,
)
