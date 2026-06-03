package ddd.kc.ui.pages.imageviewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CloseW400Outlined
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.util.logging.KcLog
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.close
import org.jetbrains.compose.resources.stringResource

private val log = KcLog.withTag("ImageViewerScreen")

class ImageViewerScreen(
    private val imageUrls: List<String>,
    private val thumbnailUrls: List<String> = emptyList(),
    private val startIndex: Int = 0,
    private val routeKey: String = nextRouteInstanceKey("image-viewer"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { imageUrls.size })
    var showUi by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
      log.i { "图片查看器 -> 打开(count=${imageUrls.size},startIndex=$startIndex)" }
      focusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                  if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                  when (event.key) {
                    Key.DirectionLeft -> {
                      val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                      if (prev != pagerState.currentPage) {
                        pagerState.requestScrollToPage(prev)
                        true
                      } else false
                    }
                    Key.DirectionRight -> {
                      val next = (pagerState.currentPage + 1).coerceAtMost(imageUrls.lastIndex)
                      if (next != pagerState.currentPage) {
                        pagerState.requestScrollToPage(next)
                        true
                      } else false
                    }
                    Key.Escape -> {
                      navigator.pop()
                      true
                    }
                    else -> false
                  }
                }
                .clickable { showUi = !showUi }
    ) {
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val fullUrl = imageUrls.getOrNull(page)
        val thumbUrl = thumbnailUrls.getOrNull(page)
        ZoomImagePage(fullUrl = fullUrl, thumbnailUrl = thumbUrl)
      }

      if (showUi) {
        Box(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
          Text(
              text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
              color = Color.White,
              style = MaterialTheme.typography.labelLarge,
          )
        }

        IconButton(
            onClick = { navigator.pop() },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
          Icon(
              imageVector = Icons.CloseW400Outlined,
              contentDescription = stringResource(Res.string.close),
              tint = Color.White,
          )
        }
      }
    }
  }
}

@Composable
private fun ZoomImagePage(fullUrl: String?, thumbnailUrl: String?) {
  val isGif = remember(fullUrl) { fullUrl?.let(::isGifUrl) == true }
  var isLoading by remember(fullUrl) { mutableStateOf(true) }

  Box(modifier = Modifier.fillMaxSize()) {
    if (isGif) {
      NetworkImage(
          url = fullUrl,
          thumbnailUrl = thumbnailUrl,
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
      return@Box
    }

    // Thumbnail shown as placeholder while full image loads
    if (isLoading && !thumbnailUrl.isNullOrBlank()) {
      AsyncImage(
          model = thumbnailUrl,
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
    }

    CoilZoomAsyncImage(
        model = fullUrl,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        onLoading = { isLoading = true },
        onSuccess = { isLoading = false },
        onError = { isLoading = false },
    )

    if (isLoading) {
      LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
          color = Color.White.copy(alpha = 0.7f),
          trackColor = Color.White.copy(alpha = 0.2f),
      )
    }
  }
}

private fun isGifUrl(url: String): Boolean {
  val pathSegment =
      url.trim().substringBefore('#').substringBefore('?').substringAfterLast('/').lowercase()
  return pathSegment.endsWith(".gif")
}
