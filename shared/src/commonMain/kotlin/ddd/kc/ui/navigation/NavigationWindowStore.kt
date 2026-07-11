package ddd.kc.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post

/** Navigator-scoped transient windows. Routes carry only opaque ids, never whole domain graphs. */
class NavigationWindowStore(private val capacity: Int = 128) {
  private val postWindows = LinkedHashMap<String, List<Post>>()
  private val creatorWindows = LinkedHashMap<String, List<Creator>>()
  private val imageWindows = LinkedHashMap<String, ImageWindow>()

  fun putPosts(posts: List<Post>): String = put(postWindows, "posts", posts.toList())

  fun posts(id: String): List<Post>? = postWindows[id]

  fun putCreators(creators: List<Creator>): String =
      put(creatorWindows, "creators", creators.toList())

  fun creators(id: String): List<Creator>? = creatorWindows[id]

  fun putImages(
      imageUrls: List<String>,
      thumbnailUrls: List<String>,
      onImageViewed: ((String) -> Unit)?,
  ): String =
      put(
          imageWindows,
          "images",
          ImageWindow(imageUrls.toList(), thumbnailUrls.toList(), onImageViewed),
      )

  fun images(id: String): ImageWindow? = imageWindows[id]

  private fun <T> put(windows: LinkedHashMap<String, T>, prefix: String, value: T): String {
    val id = nextRouteInstanceKey(prefix)
    windows[id] = value
    while (windows.size > capacity) windows.remove(windows.keys.first())
    return id
  }
}

data class ImageWindow(
    val imageUrls: List<String>,
    val thumbnailUrls: List<String>,
    val onImageViewed: ((String) -> Unit)?,
)

val LocalNavigationWindowStore =
    staticCompositionLocalOf<NavigationWindowStore> { error("NavigationWindowStore not provided") }
