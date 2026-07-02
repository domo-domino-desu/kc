package ddd.kc.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ddd.kc.data.network.PageInfo
import ddd.kc.ui.state.PaginationSnapshot
import kotlinx.coroutines.flow.distinctUntilChanged

val LazyListState.isAtTop: Boolean
  get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyGridState.isAtTop: Boolean
  get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

fun shouldRefreshOnRepeatSelection(isAtTop: Boolean, visiblePageInfo: PageInfo?): Boolean =
    isAtTop && (visiblePageInfo?.currentPage ?: 1) == 1

@Composable
fun <T> PageStateContent(
    snapshot: PaginationSnapshot<T>,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = {
      ListLoadingSkeleton(modifier = modifier.padding(horizontal = 12.dp))
    },
    content: @Composable () -> Unit,
) {
  when {
    snapshot.loading -> loadingContent()
    !snapshot.errorMessage.isNullOrBlank() -> {
      ErrorToastEffect(snapshot.errorMessage)
      Box(modifier = modifier.fillMaxSize())
    }
    else -> content()
  }
}

@Composable
private fun AutoLoadEffectCore(
    getLastVisible: () -> Int?,
    key: Any,
    totalItems: Int,
    threshold: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
  LaunchedEffect(key, totalItems) {
    snapshotFlow { getLastVisible() }
        .distinctUntilChanged()
        .collect { lastVisible ->
          if (
              lastVisible != null &&
                  lastVisible >= totalItems - threshold &&
                  hasMore &&
                  !isLoadingMore
          ) {
            onLoadMore()
          }
        }
  }
}

@Composable
fun AutoLoadEffect(
    listState: LazyListState,
    totalItems: Int,
    threshold: Int = 10,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
  AutoLoadEffectCore(
      getLastVisible = { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
      key = listState,
      totalItems = totalItems,
      threshold = threshold,
      hasMore = hasMore,
      isLoadingMore = isLoadingMore,
      onLoadMore = onLoadMore,
  )
}

@Composable
private fun AutoLoadPreviousEffectCore(
    getFirstVisible: () -> Int?,
    key: Any,
    totalItems: Int,
    threshold: Int,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
    onLoadPrevious: () -> Unit,
) {
  LaunchedEffect(key, totalItems) {
    snapshotFlow { getFirstVisible() }
        .distinctUntilChanged()
        .collect { firstVisible ->
          if (
              firstVisible != null &&
                  firstVisible <= threshold &&
                  totalItems > 0 &&
                  hasPrevious &&
                  !isLoadingPrevious
          ) {
            onLoadPrevious()
          }
        }
  }
}

@Composable
fun AutoLoadPreviousEffect(
    listState: LazyListState,
    totalItems: Int,
    threshold: Int = 3,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
    onLoadPrevious: () -> Unit,
) {
  AutoLoadPreviousEffectCore(
      getFirstVisible = { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index },
      key = listState,
      totalItems = totalItems,
      threshold = threshold,
      hasPrevious = hasPrevious,
      isLoadingPrevious = isLoadingPrevious,
      onLoadPrevious = onLoadPrevious,
  )
}

@Composable
fun AutoLoadPreviousEffect(
    gridState: LazyGridState,
    totalItems: Int,
    threshold: Int = 3,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
    onLoadPrevious: () -> Unit,
) {
  AutoLoadPreviousEffectCore(
      getFirstVisible = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index },
      key = gridState,
      totalItems = totalItems,
      threshold = threshold,
      hasPrevious = hasPrevious,
      isLoadingPrevious = isLoadingPrevious,
      onLoadPrevious = onLoadPrevious,
  )
}

@Composable
fun AutoLoadEffect(
    gridState: LazyGridState,
    totalItems: Int,
    threshold: Int = 10,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
  AutoLoadEffectCore(
      getLastVisible = { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
      key = gridState,
      totalItems = totalItems,
      threshold = threshold,
      hasMore = hasMore,
      isLoadingMore = isLoadingMore,
      onLoadMore = onLoadMore,
  )
}

@Composable
private fun FooterLoadingItem() {
  Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
    SkeletonBlock(modifier = Modifier.fillMaxWidth().height(44.dp))
  }
}

fun LazyGridScope.loadingFooter(
    isLoadingMore: Boolean,
    appendErrorMessage: String?,
) {
  if (isLoadingMore) {
    item(key = "__loading_footer__", span = { GridItemSpan(maxLineSpan) }) { FooterLoadingItem() }
  }
}

fun LazyListScope.loadingFooter(
    isLoadingMore: Boolean,
    appendErrorMessage: String?,
) {
  if (isLoadingMore) {
    item(key = "__loading_footer__") { FooterLoadingItem() }
  }
}
