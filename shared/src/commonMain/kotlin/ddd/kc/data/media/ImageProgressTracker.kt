package ddd.kc.data.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class ImageDownloadProgressSnapshot(
    val bytesRead: Long,
    val totalBytes: Long,
) {
  val progressFraction: Float? =
      if (totalBytes > 0L) {
        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
      } else {
        null
      }
}

private const val MAX_TRACKED_IMAGE_REQUEST_COUNT = 256

class ImageProgressTracker {
  private val snapshotsByKey =
      MutableStateFlow<Map<String, ImageDownloadProgressSnapshot>>(emptyMap())

  fun observe(progressKey: String): Flow<ImageDownloadProgressSnapshot?> {
    val normalizedKey = normalizeProgressKey(progressKey)
    if (normalizedKey.isBlank()) return flowOf(null)
    return snapshotsByKey.map { snapshots -> snapshots[normalizedKey] }.distinctUntilChanged()
  }

  fun markRequestStarted(progressKey: String) {
    updateSnapshot(progressKey) { current ->
      current?.takeIf { it.bytesRead > 0L } ?: ImageDownloadProgressSnapshot(0L, -1L)
    }
  }

  fun updateDownloadProgress(progressKey: String, bytesRead: Long, totalBytes: Long) {
    updateSnapshot(progressKey) {
      ImageDownloadProgressSnapshot(
          bytesRead = bytesRead.coerceAtLeast(0L),
          totalBytes = totalBytes,
      )
    }
  }

  fun clear(progressKey: String) {
    updateSnapshot(progressKey) { null }
  }

  private fun updateSnapshot(
      progressKey: String,
      updater: (ImageDownloadProgressSnapshot?) -> ImageDownloadProgressSnapshot?,
  ) {
    val normalizedKey = normalizeProgressKey(progressKey)
    if (normalizedKey.isBlank()) return
    snapshotsByKey.update { current ->
      val mutable = current.toMutableMap()
      val nextValue = updater(mutable[normalizedKey])
      if (nextValue == null) {
        mutable.remove(normalizedKey)
      } else {
        mutable[normalizedKey] = nextValue
      }
      val overflow = mutable.size - MAX_TRACKED_IMAGE_REQUEST_COUNT
      if (overflow > 0) mutable.keys.take(overflow).toList().forEach(mutable::remove)
      mutable.toMap()
    }
  }
}

fun normalizeProgressKey(progressKey: String): String = progressKey.trim()
