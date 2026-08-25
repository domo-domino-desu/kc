package ddd.kc.ui.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
  ): String {
    val snapshot =
        ImageWindowSnapshot(
            images =
                imageUrls.mapIndexed { index, url ->
                  ImageViewerItem(
                      key = "$index\u001f$url",
                      imageUrl = url,
                      thumbnailUrl = thumbnailUrls.getOrNull(index),
                  )
                }
        )
    return putImageWindow(
        ImageWindow(
            initialSnapshot = snapshot,
            snapshots = flowOf(snapshot),
            onImageViewed = onImageViewed,
        )
    )
  }

  fun putImageWindow(window: ImageWindow): String = put(imageWindows, "images", window)

  fun images(id: String): ImageWindow? = imageWindows[id]

  private fun <T> put(windows: LinkedHashMap<String, T>, prefix: String, value: T): String {
    val id = nextRouteInstanceKey(prefix)
    windows[id] = value
    while (windows.size > capacity) windows.remove(windows.keys.first())
    return id
  }
}

data class ImageWindow(
    val initialSnapshot: ImageWindowSnapshot,
    val snapshots: Flow<ImageWindowSnapshot>,
    val onImageViewed: ((String) -> Unit)?,
    val onLoadPrevious: (() -> Unit)? = null,
    val onLoadNext: (() -> Unit)? = null,
)

data class ImageViewerItem(
    val key: String,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
)

data class ImageWindowSnapshot(
    val images: List<ImageViewerItem>,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isLoadingPrevious: Boolean = false,
    val isLoadingNext: Boolean = false,
    val previousError: QueryError? = null,
    val nextError: QueryError? = null,
)

val LocalNavigationWindowStore =
    staticCompositionLocalOf<NavigationWindowStore> { error("NavigationWindowStore not provided") }
