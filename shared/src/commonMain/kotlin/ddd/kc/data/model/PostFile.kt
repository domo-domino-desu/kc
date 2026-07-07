package ddd.kc.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostFile(
    val name: String? = null,
    val path: String? = null,
    val deferred: Boolean = false,
) : PlatformSerializable

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl", "bmp")
private val videoExtensions =
    setOf("mp4", "m4v", "mov", "webm", "avi", "wmv", "flv", "mpeg", "mpg", "3gp", "mkv")
private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "wma")

private fun PostFile.ext(): String = (name ?: path)?.substringAfterLast('.')?.lowercase() ?: ""

fun PostFile.hasPath(): Boolean = !path.isNullOrBlank()

fun PostFile.canLoadFullImage(post: Post): Boolean = hasPath() && !deferred && post.hasFull != false

fun PostFile.isImage(): Boolean = ext() in imageExtensions

fun PostFile.isGif(): Boolean = ext() == "gif"

fun PostFile.isVideo(): Boolean = ext() in videoExtensions

fun PostFile.isAudio(): Boolean = ext() in audioExtensions

fun PostFile.fullUrl(cdnUrl: String): String? {
  val p = path ?: return null
  val fileUrl = cdnUrl.replace("://img.", "://file.")
  val base = "$fileUrl/data$p"
  val fileName = name ?: p.substringAfterLast('/')
  return if (fileName.isNotBlank()) "$base?f=${encodeUrlComponent(fileName)}" else base
}

fun PostFile.thumbnailUrl(cdnUrl: String): String? {
  val p = path ?: return null
  return "$cdnUrl/thumbnail/data$p"
}

private fun encodeUrlComponent(s: String): String = buildString {
  for (c in s) {
    when {
      c.isLetterOrDigit() || c in "-._~" -> append(c)
      else -> {
        val bytes = c.toString().toByteArray(Charsets.UTF_8)
        for (b in bytes) append('%')
            .append(b.toInt().and(0xff).toString(16).padStart(2, '0').uppercase())
      }
    }
  }
}
