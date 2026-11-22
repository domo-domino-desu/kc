package ddd.kc.data.model

import ddd.kc.fake.TestFixtures
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.Json

class TemporaryMediaTest {
  private val json = Json { ignoreUnknownKeys = true }
  private val cdn = "https://img.pawchive.pw"

  private fun post(name: String) = json.decodeFromString<Post>(TestFixtures.read("live/$name.json"))

  private fun validBeforeExpiry(name: String): Instant {
    val file = post(name).allFiles().first { it.isTemporary() }
    val expiry = Url(file.tempUrl ?: file.tempDownloadUrl!!).parameters["e"]!!.toLong()
    return Instant.fromEpochSeconds(expiry - 120)
  }

  @Test
  fun realPreviewImagesUseTemporaryOriginalsInsteadOfPermanentPreviewPaths() {
    val name = "post-temporary-images"
    val post = post(name)
    val file = post.imageFiles().first { it.isTemporary() }
    val now = validBeforeExpiry(name)
    assertTrue(file.previewOnly)
    assertTrue(file.isTemporary())
    assertTrue(file.canLoadFullImage(now))
    assertEquals(file.tempUrl, file.fullUrl(cdn, now))
    assertNotEquals(file.fullUrl(cdn, now), file.thumbnailUrl(cdn))
    assertTrue(file.thumbnailUrl(cdn)!!.contains("/thumbnail/data/"))
    val afterRetention = file.temporaryExpiresAt()!! + 1.seconds
    assertTrue(file.isExpired(afterRetention))
    assertFalse(file.canLoadFullImage(afterRetention))
    assertFalse(file.canDownload(afterRetention))
    assertNull(file.fullUrl(cdn, afterRetention))
    assertNull(file.downloadUrl(cdn, afterRetention))
    assertNotNull(file.thumbnailUrl(cdn))
  }

  @Test
  fun realVideoUsesHlsForPlaybackAndMp4ForDownloading() {
    val name = "post-temporary-video"
    val post = post(name)
    val file = post.allFiles().first { it.isVideo() }
    val now = validBeforeExpiry(name)
    assertTrue(file.deferred)
    assertTrue(file.canDownload(now))
    assertTrue(Url(file.fullUrl(cdn, now)!!).encodedPath.endsWith(".m3u8"))
    assertTrue(Url(file.downloadUrl(cdn, now)!!).encodedPath.endsWith(".mp4"))
    assertNotEquals(file.fullUrl(cdn, now), file.downloadUrl(cdn, now))
    val signatureExpires = Url(file.tempUrl!!).parameters["e"]!!.toLong()
    val afterSignature = Instant.fromEpochSeconds(signatureExpires + 1)
    assertFalse(file.isExpired(afterSignature))
    assertTrue(file.needsTemporaryUrlRefresh(afterSignature))
    assertFalse(file.needsTemporaryUrlRefresh(now))
  }

  @Test
  fun realDeferredArchiveCanDownloadWithoutClaimingTheWholePostIsArchived() {
    val name = "post-temporary-archive"
    val post = post(name)
    val file = post.attachments.first { it.deferred }
    assertEquals(false, post.hasFull)
    assertTrue(post.imageFiles().all { it.canLoadFullImage(validBeforeExpiry(name)) })
    assertTrue(file.isTemporary())
    assertTrue(file.canDownload(validBeforeExpiry(name)))
    assertEquals(file.tempUrl, file.downloadUrl(cdn, validBeforeExpiry(name)))
  }

  @Test
  fun realUnavailableOriginalDoesNotFallBackToAnInventedPermanentUrl() {
    val post = post("post-unavailable-original")
    val file = post.attachments.first { it.deferred }
    assertFalse(file.isTemporary())
    assertFalse(file.isExpired())
    assertFalse(file.canDownload())
    assertNull(file.downloadUrl(cdn))
    assertNull(file.fullUrl(cdn))
    assertTrue(post.imageFiles().any { it.previewOnly && !it.canLoadFullImage() })
  }
}
