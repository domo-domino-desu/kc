package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordChannel(
    val id: String,
    val name: String,
)

@Serializable
data class DiscordAuthor(
    val id: String = "",
    val avatar: String? = null,
    val username: String = "",
    val discriminator: String? = null,
    @SerialName("public_flags") val publicFlags: Int? = null,
)

@Serializable
data class DiscordAttachment(
    val name: String? = null,
    val path: String? = null,
)

@Serializable
data class DiscordPost(
    val id: String,
    val author: DiscordAuthor = DiscordAuthor(),
    val server: String = "",
    val channel: String = "",
    val content: String? = null,
    val added: String? = null,
    val published: String? = null,
    val edited: String? = null,
    val attachments: List<DiscordAttachment> = emptyList(),
)

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl", "bmp")
private val videoExtensions =
    setOf("mp4", "m4v", "mov", "webm", "avi", "wmv", "flv", "mpeg", "mpg", "3gp", "mkv")

private fun DiscordAttachment.ext(): String =
    (name ?: path)?.substringAfterLast('.')?.lowercase() ?: ""

fun DiscordAttachment.isImage(): Boolean = ext() in imageExtensions

fun DiscordAttachment.isVideo(): Boolean = ext() in videoExtensions

fun DiscordAttachment.fullUrl(cdnUrl: String): String? {
  val p = path ?: return null
  return "$cdnUrl/data$p"
}

fun DiscordAttachment.thumbnailUrl(cdnUrl: String): String? {
  val p = path ?: return null
  return "$cdnUrl/thumbnail/data$p"
}

fun DiscordAuthor.avatarUrl(): String? {
  val a = avatar ?: return null
  if (id.isBlank()) return null
  return "https://cdn.discordapp.com/avatars/$id/$a.png"
}
