package ddd.kc.data.model

import io.ktor.http.Url
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostFile(
    val name: String? = null,
    val path: String? = null,
    val deferred: Boolean = false,
    @SerialName("preview_only") val previewOnly: Boolean = false,
    @SerialName("temp_url") val tempUrl: String? = null,
    @SerialName("temp_download_url") val tempDownloadUrl: String? = null,
    @SerialName("temp_expires") val tempExpires: String? = null,
)

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl", "bmp")
private val videoExtensions =
    setOf("mp4", "m4v", "mov", "webm", "avi", "wmv", "flv", "mpeg", "mpg", "3gp", "mkv")
private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "wma")

private fun PostFile.ext(): String = (name ?: path)?.substringAfterLast('.')?.lowercase() ?: ""

fun PostFile.hasPath(): Boolean = !path.isNullOrBlank()

fun PostFile.isTemporary(): Boolean =
    !tempUrl.isNullOrBlank() || !tempDownloadUrl.isNullOrBlank() || !tempExpires.isNullOrBlank()

fun PostFile.temporaryExpiresAt(): Instant? =
    tempExpires?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun PostFile.isExpired(now: Instant = Clock.System.now()): Boolean =
    temporaryExpiresAt()?.let { it <= now } == true

fun PostFile.needsTemporaryUrlRefresh(now: Instant = Clock.System.now()): Boolean {
  if (!isTemporary() || isExpired(now)) return false
  val urls = listOfNotNull(tempUrl, tempDownloadUrl).filter { it.isNotBlank() }
  return urls.isEmpty() ||
      urls.any { url ->
        val expiry = runCatching { Url(url).parameters["e"]?.toLongOrNull() }.getOrNull()
        expiry != null && expiry <= now.epochSeconds + 30
      }
}

fun PostFile.canLoadFullImage(now: Instant = Clock.System.now()): Boolean =
    !isExpired(now) &&
        if (isTemporary()) safeTemporaryUrl(tempUrl ?: tempDownloadUrl) != null
        else hasPath() && !deferred && !previewOnly

fun PostFile.isImage(): Boolean = ext() in imageExtensions

fun PostFile.isGif(): Boolean = ext() == "gif"

fun PostFile.isVideo(): Boolean = ext() in videoExtensions

fun PostFile.isAudio(): Boolean = ext() in audioExtensions

fun PostFile.fullUrl(cdnUrl: String, now: Instant = Clock.System.now()): String? {
  if (isExpired(now)) return null
  if (isTemporary()) return safeTemporaryUrl(tempUrl ?: tempDownloadUrl)
  if (deferred || previewOnly) return null
  val p = path ?: return null
  val fileUrl = cdnUrl.replace("://img.", "://file.")
  val base = "$fileUrl/data$p"
  val fileName = name ?: p.substringAfterLast('/')
  return if (fileName.isNotBlank()) "$base?f=${encodeUrlComponent(fileName)}" else base
}

fun PostFile.downloadUrl(cdnUrl: String, now: Instant = Clock.System.now()): String? =
    if (isExpired(now)) null
    else if (isTemporary()) safeTemporaryUrl(tempDownloadUrl ?: tempUrl) else fullUrl(cdnUrl, now)

fun PostFile.canDownload(now: Instant = Clock.System.now()): Boolean =
    !isExpired(now) &&
        if (isTemporary()) safeTemporaryUrl(tempDownloadUrl ?: tempUrl) != null
        else hasPath() && !deferred && !previewOnly

private fun safeTemporaryUrl(value: String?): String? =
    value?.takeIf { raw ->
      runCatching {
            val url = Url(raw)
            url.protocol.name == "https" &&
                url.host.isNotBlank() &&
                url.user.isNullOrEmpty() &&
                url.password.isNullOrEmpty()
          }
          .getOrDefault(false)
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
