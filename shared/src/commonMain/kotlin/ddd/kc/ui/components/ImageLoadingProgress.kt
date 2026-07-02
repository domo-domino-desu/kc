package ddd.kc.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ddd.kc.data.media.ImageDownloadProgressSnapshot
import ddd.kc.data.media.ImageProgressTracker
import ddd.kc.data.media.normalizeProgressKey
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject

enum class ImageLoadLifecycleState {
  Idle,
  Loading,
  Success,
  Error,
}

sealed interface ImageLoadProgressState {
  data object Idle : ImageLoadProgressState

  data object LoadingUnknown : ImageLoadProgressState

  data class LoadingKnown(
      val progress: Float,
      val bytesRead: Long,
      val totalBytes: Long,
  ) : ImageLoadProgressState

  data object Success : ImageLoadProgressState

  data object Error : ImageLoadProgressState
}

private fun resolveImageLoadProgressState(
    lifecycleState: ImageLoadLifecycleState,
    progressSnapshot: ImageDownloadProgressSnapshot?,
): ImageLoadProgressState =
    when (lifecycleState) {
      ImageLoadLifecycleState.Idle -> ImageLoadProgressState.Idle
      ImageLoadLifecycleState.Success -> ImageLoadProgressState.Success
      ImageLoadLifecycleState.Error -> ImageLoadProgressState.Error
      ImageLoadLifecycleState.Loading -> {
        val progress = progressSnapshot?.progressFraction
        if (progress != null) {
          ImageLoadProgressState.LoadingKnown(
              progress = progress,
              bytesRead = progressSnapshot.bytesRead,
              totalBytes = progressSnapshot.totalBytes,
          )
        } else {
          ImageLoadProgressState.LoadingUnknown
        }
      }
    }

@Composable
fun rememberImageLoadProgressState(
    progressKey: String?,
    lifecycleState: ImageLoadLifecycleState,
    tracker: ImageProgressTracker = koinInject(),
): ImageLoadProgressState {
  val normalizedKey =
      remember(progressKey) { progressKey?.let(::normalizeProgressKey)?.takeIf { it.isNotBlank() } }
  val progressFlow =
      remember(tracker, normalizedKey) { normalizedKey?.let(tracker::observe) ?: flowOf(null) }
  val progressSnapshot by progressFlow.collectAsState(initial = null)

  LaunchedEffect(tracker, normalizedKey, lifecycleState) {
    val key = normalizedKey ?: return@LaunchedEffect
    if (
        lifecycleState == ImageLoadLifecycleState.Success ||
            lifecycleState == ImageLoadLifecycleState.Error
    ) {
      tracker.clear(key)
    }
  }

  return remember(lifecycleState, progressSnapshot) {
    resolveImageLoadProgressState(lifecycleState, progressSnapshot)
  }
}

@Composable
fun TopLinearImageLoadingProgress(
    progressState: ImageLoadProgressState,
    modifier: Modifier = Modifier,
) {
  val indicatorModifier = modifier.height(2.dp)
  when (progressState) {
    is ImageLoadProgressState.LoadingKnown ->
        LinearProgressIndicator(
            progress = { progressState.progress },
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )
    ImageLoadProgressState.LoadingUnknown ->
        LinearProgressIndicator(
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )
    else -> Unit
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterCircularWavyImageLoadingProgress(
    progressState: ImageLoadProgressState,
    modifier: Modifier = Modifier,
) {
  val indicatorModifier = modifier.size(52.dp)
  when (progressState) {
    is ImageLoadProgressState.LoadingKnown ->
        CircularWavyProgressIndicator(
            progress = { progressState.progress },
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )
    ImageLoadProgressState.LoadingUnknown ->
        CircularWavyProgressIndicator(
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )
    else -> Unit
  }
}
