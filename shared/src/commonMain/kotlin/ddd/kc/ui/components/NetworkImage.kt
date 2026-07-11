package ddd.kc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import ddd.kc.utils.logging.KcLog

private val networkImageLog = KcLog.withTag("NetworkImage")

@Composable
fun NetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    thumbnailUrl: String? = null,
    showTopLinearLoadingProgress: Boolean = false,
    progressTrackingKey: String? = null,
    fallbackContent: (@Composable () -> Unit)? = null,
    logFailureAsWarning: Boolean = true,
) {
  val fallbackUrl = thumbnailUrl?.takeIf { it.isNotBlank() && it != url }
  if (url.isNullOrBlank()) {
    if (fallbackUrl.isNullOrBlank()) {
      Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        fallbackContent?.invoke()
      }
    } else {
      NetworkImage(
          url = fallbackUrl,
          modifier = modifier,
          contentScale = contentScale,
          contentDescription = contentDescription,
          showTopLinearLoadingProgress = false,
          fallbackContent = fallbackContent,
          logFailureAsWarning = logFailureAsWarning,
      )
    }
    return
  }
  var loadLifecycleState by remember(url) { mutableStateOf(ImageLoadLifecycleState.Idle) }
  val progressState =
      if (showTopLinearLoadingProgress) {
        rememberImageLoadProgressState(
            progressKey = progressTrackingKey ?: url,
            lifecycleState = loadLifecycleState,
        )
      } else {
        ImageLoadProgressState.Idle
      }
  Box(modifier = modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
        onLoading = { loadLifecycleState = ImageLoadLifecycleState.Loading },
        onSuccess = { loadLifecycleState = ImageLoadLifecycleState.Success },
        loading = {
          if (!fallbackUrl.isNullOrBlank()) {
            NetworkImage(
                url = fallbackUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                contentDescription = contentDescription,
                showTopLinearLoadingProgress = false,
                fallbackContent = fallbackContent,
                logFailureAsWarning = logFailureAsWarning,
            )
          } else if (fallbackContent != null) {
            fallbackContent()
          } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
          }
        },
        error = { error ->
          loadLifecycleState = ImageLoadLifecycleState.Error
          LaunchedEffect(url, fallbackUrl, error) {
            val message =
                "图片加载失败(urlLength=${url.length},hasFallback=${!fallbackUrl.isNullOrBlank()})"
            if (logFailureAsWarning) {
              networkImageLog.w(error.result.throwable) { message }
            } else {
              networkImageLog.d(error.result.throwable) { message }
            }
          }
          if (!fallbackUrl.isNullOrBlank()) {
            NetworkImage(
                url = fallbackUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                contentDescription = contentDescription,
                showTopLinearLoadingProgress = false,
                fallbackContent = fallbackContent,
                logFailureAsWarning = logFailureAsWarning,
            )
          } else if (fallbackContent != null) {
            fallbackContent()
          } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
          }
        },
        success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
    )
    if (showTopLinearLoadingProgress) {
      TopLinearImageLoadingProgress(
          progressState = progressState,
          modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
