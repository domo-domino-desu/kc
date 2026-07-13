package ddd.kc.ui.app.navigation

import ddd.kc.data.model.PostKey
import ddd.kc.ui.pages.post.PostRouteScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppScreenSerializationTest {
  @Test
  fun postScreenRoundTripKeepsEntryKey() {
    val screen =
        PostRouteScreen(
            windowId = "window",
            resourceKey = PostKey("service", "creator", "post"),
            startIndex = 0,
        )

    val restored = Json.decodeFromString<PostRouteScreen>(Json.encodeToString(screen))

    assertEquals(screen.key, restored.key)
  }

  @Test
  fun repeatedPostScreenHasDistinctEntryKeys() {
    val key = PostKey("service", "creator", "post")

    assertNotEquals(
        PostRouteScreen("window", key, 0).key,
        PostRouteScreen("window", key, 0).key,
    )
  }
}
