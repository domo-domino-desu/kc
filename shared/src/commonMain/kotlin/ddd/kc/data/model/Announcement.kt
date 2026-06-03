package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Announcement(
    val service: String = "",
    @SerialName("user_id") val userId: String = "",
    val hash: String = "",
    val content: String = "",
    val added: String? = null,
)
