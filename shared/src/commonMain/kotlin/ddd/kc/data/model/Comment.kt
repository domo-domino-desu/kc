package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: String,
    val content: String = "",
    val published: String? = null,
    @SerialName("commenter") val commenter: String? = null,
    @SerialName("commenter_name") val commenterName: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
)
