package ddd.kc.ui.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ImageWindowTest {
  @Test
  fun staticImageWindowKeepsImageAndThumbnailIndexesAligned() {
    val store = NavigationWindowStore()
    val id =
        store.putImages(
            imageUrls = listOf("https://example.test/a.png", "https://example.test/b.png"),
            thumbnailUrls = listOf("https://example.test/a-thumb.png"),
            onImageViewed = null,
        )

    val images = store.images(id)!!.initialSnapshot.images

    assertEquals(2, images.size)
    assertEquals("https://example.test/a-thumb.png", images[0].thumbnailUrl)
    assertNull(images[1].thumbnailUrl)
    assertNotEquals(images[0].key, images[1].key)
  }
}
