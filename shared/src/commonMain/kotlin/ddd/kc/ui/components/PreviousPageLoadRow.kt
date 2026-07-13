package ddd.kc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.load_previous_here
import kc.shared.generated.resources.loading_previous_page
import kc.shared.generated.resources.page_previous
import kc.shared.generated.resources.previous_page_failed
import org.jetbrains.compose.resources.stringResource

@Composable
fun PagedPullRefreshBox(
    currentPage: Int,
    enabled: Boolean,
    refreshing: Boolean,
    loadingPrevious: Boolean,
    onRefresh: () -> Unit,
    onLoadPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
  KcPullRefreshBox(
      enabled = enabled && !loadingPrevious,
      refreshing = refreshing || loadingPrevious,
      onRefresh = { if (currentPage <= 1) onRefresh() else onLoadPrevious() },
      modifier = modifier,
      content = content,
  )
}

@Composable
fun PreviousPageLoadRow(
    canLoadPrevious: Boolean,
    loadingPrevious: Boolean,
    errorMessage: String?,
    onLoadPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (!canLoadPrevious && !loadingPrevious && errorMessage.isNullOrBlank()) return
  Row(
      modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text =
            when {
              loadingPrevious -> stringResource(Res.string.loading_previous_page)
              !errorMessage.isNullOrBlank() -> stringResource(Res.string.previous_page_failed)
              else -> stringResource(Res.string.load_previous_here)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (loadingPrevious) {
      CircularProgressIndicator(modifier = Modifier.padding(6.dp), strokeWidth = 2.dp)
    } else if (canLoadPrevious) {
      AssistChip(
          onClick = onLoadPrevious,
          label = { Text(stringResource(Res.string.page_previous)) },
      )
    }
  }
}

fun LazyGridScope.previousPageHeader(
    canLoadPrevious: Boolean,
    loadingPrevious: Boolean,
    errorMessage: String?,
    onLoadPrevious: () -> Unit,
) {
  if (!canLoadPrevious && !loadingPrevious && errorMessage.isNullOrBlank()) return
  item(key = "__previous_page__", span = { GridItemSpan(maxLineSpan) }) {
    PreviousPageLoadRow(
        canLoadPrevious = canLoadPrevious,
        loadingPrevious = loadingPrevious,
        errorMessage = errorMessage,
        onLoadPrevious = onLoadPrevious,
    )
  }
}

fun LazyListScope.previousPageHeader(
    canLoadPrevious: Boolean,
    loadingPrevious: Boolean,
    errorMessage: String?,
    onLoadPrevious: () -> Unit,
) {
  if (!canLoadPrevious && !loadingPrevious && errorMessage.isNullOrBlank()) return
  item(key = "__previous_page__") {
    PreviousPageLoadRow(
        canLoadPrevious = canLoadPrevious,
        loadingPrevious = loadingPrevious,
        errorMessage = errorMessage,
        onLoadPrevious = onLoadPrevious,
    )
  }
}
