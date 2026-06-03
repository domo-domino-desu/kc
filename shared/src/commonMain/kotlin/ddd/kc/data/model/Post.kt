package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("shared_file") val sharedFile: Boolean = false,
    @SerialName("embed") val embed: PostEmbed? = null,
    val poll: PostPoll? = null,
    val captions: List<PostCaption>? = null,
    val tags: List<String>? = null,
)

val Post.creatorId: String
  get() = artistId ?: user

@Serializable
data class PostEmbed(
    val url: String? = null,
    val subject: String? = null,
    val description: String? = null,
)

@Serializable
data class PostPoll(
    val options: List<PostPollOption> = emptyList(),
)

@Serializable data class PostPollOption(val text: String = "")

@Serializable
data class PostCaption(
    val language: String? = null,
    val content: String? = null,
)

fun Post.allFiles(): List<PostFile> = buildList {
  file?.let { add(it) }
  addAll(attachments)
}

fun Post.imageFiles(): List<PostFile> = allFiles().filter { it.isImage() }

fun Post.thumbnailUrl(cdnUrl: String): String? = imageFiles().firstOrNull()?.thumbnailUrl(cdnUrl)
