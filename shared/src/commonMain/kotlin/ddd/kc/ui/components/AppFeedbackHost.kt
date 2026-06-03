package ddd.kc.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class AppFeedbackRequest(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null,
)

val LocalShowFeedback: ProvidableCompositionLocal<(AppFeedbackRequest) -> Unit> =
    staticCompositionLocalOf {
      {}
    }

val LocalShowToast: ProvidableCompositionLocal<(String) -> Unit> = staticCompositionLocalOf { {} }

@Composable
fun AppFeedbackHost(content: @Composable () -> Unit) {
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val showFeedback =
      remember(snackbarHostState, coroutineScope) {
        { request: AppFeedbackRequest ->
          val normalized = request.message.trim()
          if (normalized.isNotBlank()) {
            coroutineScope.launch {
              snackbarHostState.currentSnackbarData?.dismiss()
              val actionLabel = request.actionLabel?.trim()?.ifBlank { null }
              val result =
                  snackbarHostState.showSnackbar(
                      message = normalized,
                      actionLabel = actionLabel,
                      duration =
                          if (actionLabel == null) SnackbarDuration.Short
                          else SnackbarDuration.Long,
                  )
              if (result == SnackbarResult.ActionPerformed) {
                request.onAction?.invoke()
              }
            }
          }
        }
      }
  val showToast =
      remember(showFeedback) { { message: String -> showFeedback(AppFeedbackRequest(message)) } }
  CompositionLocalProvider(
      LocalShowFeedback provides showFeedback,
      LocalShowToast provides showToast,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      content()
      SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
      )
    }
  }
}

@Composable
fun ErrorToastEffect(message: String?) {
  val showToast = LocalShowToast.current
  val normalized = message?.trim().orEmpty()
  LaunchedEffect(normalized) { if (normalized.isNotBlank()) showToast(normalized) }
}
