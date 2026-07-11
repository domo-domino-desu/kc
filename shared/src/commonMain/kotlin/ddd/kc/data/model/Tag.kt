package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val tag: String,
    @SerialName("post_count") val count: Int = 0,
)
