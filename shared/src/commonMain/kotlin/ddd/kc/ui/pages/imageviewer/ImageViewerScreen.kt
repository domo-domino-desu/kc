package ddd.kc.ui.pages.imageviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.ui.app.i18n.localizedMessage
import ddd.kc.ui.app.navigation.AppScreen
import ddd.kc.ui.app.navigation.ImageViewerItem
import ddd.kc.ui.app.navigation.LocalNavigationWindowStore
import ddd.kc.ui.app.navigation.nextRouteInstanceKey
import ddd.kc.utils.logging.KcLog
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.navigation_content_expired
import kc.shared.generated.resources.retry
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

private val log = KcLog.withTag("ImageViewerScreen")

@Serializable
class ImageViewerScreen(
    private val windowId: String,
    private val startIndex: Int = 0,
    private val routeKey: String = nextRouteInstanceKey("image-viewer"),
) : AppScreen {
  override val key: String = routeKey

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val imageWindow = LocalNavigationWindowStore.current.images(windowId)
    if (imageWindow == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.navigation_content_expired),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable { navigator.pop() },
        )
      }
      return
    }

    val snapshot by imageWindow.snapshots.collectAsState(initial = imageWindow.initialSnapshot)
    var currentImage by remember { mutableStateOf<ImageViewerItem?>(null) }
    val currentIndex = snapshot.images.indexOfFirst { it.key == currentImage?.key }

    LaunchedEffect(Unit) {
      log.i { "图片查看器 -> 打开(count=${snapshot.images.size},startIndex=$startIndex)" }
    }
    LaunchedEffect(
        currentImage?.key,
        currentIndex,
        snapshot.hasPrevious,
        snapshot.hasNext,
        snapshot.isLoadingPrevious,
        snapshot.isLoadingNext,
        snapshot.previousError,
        snapshot.nextError,
        snapshot.images.size,
    ) {
      if (
          currentIndex == 0 &&
              snapshot.hasPrevious &&
              !snapshot.isLoadingPrevious &&
              snapshot.previousError == null
      ) {
        imageWindow.onLoadPrevious?.invoke()
      }
      if (
          currentIndex == snapshot.images.lastIndex &&
              snapshot.hasNext &&
              !snapshot.isLoadingNext &&
              snapshot.nextError == null
      ) {
        imageWindow.onLoadNext?.invoke()
      }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      ZoomImageViewer(
          images = snapshot.images,
          startIndex = startIndex,
          hasUnloadedImages = snapshot.hasPrevious || snapshot.hasNext,
          onClose = { navigator.pop() },
          onImageChanged = { item ->
            currentImage = item
            imageWindow.onImageViewed?.invoke(item.imageUrl)
          },
      )

      ImagePagingStatus(
          loading = snapshot.isLoadingPrevious,
          errorMessage = snapshot.previousError?.localizedMessage(),
          onRetry = { imageWindow.onLoadPrevious?.invoke() },
          modifier = Modifier.align(Alignment.CenterStart),
      )
      ImagePagingStatus(
          loading = snapshot.isLoadingNext,
          errorMessage = snapshot.nextError?.localizedMessage(),
          onRetry = { imageWindow.onLoadNext?.invoke() },
          modifier = Modifier.align(Alignment.CenterEnd),
      )
    }
  }
}

@Composable
private fun ImagePagingStatus(
    loading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (!loading && errorMessage == null) return
  Row(
      modifier = modifier.safeDrawingPadding().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (loading) {
      CircularProgressIndicator(strokeWidth = 2.dp)
    } else {
      TextButton(onClick = onRetry) { Text("${stringResource(Res.string.retry)} · $errorMessage") }
    }
  }
}
