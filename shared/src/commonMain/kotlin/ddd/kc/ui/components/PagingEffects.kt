package ddd.kc.ui.components

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.PageInfo
import kotlinx.coroutines.flow.distinctUntilChanged

val LazyListState.isAtTop: Boolean
  get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyGridState.isAtTop: Boolean
  get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

fun shouldRefreshOnRepeatSelection(isAtTop: Boolean, visiblePageInfo: PageInfo?): Boolean =
    isAtTop && (visiblePageInfo?.currentPage ?: 1) == 1

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
    key: Any,
    totalItems: Int,
    threshold: Int,
    confirmDistanceFraction: Float,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
    getSample: () -> PreviousLoadScrollSample,
    onLoadPrevious: () -> Unit,
) {
  var triggerState by remember(key) { mutableStateOf(PreviousLoadTriggerState()) }
  LaunchedEffect(key, totalItems, hasPrevious, isLoadingPrevious, confirmDistanceFraction) {
    snapshotFlow { getSample() }
        .distinctUntilChanged()
        .collect { sample ->
          val result =
              updatePreviousLoadTriggerState(
                  state = triggerState,
                  sample = sample,
                  totalItems = totalItems,
                  threshold = threshold,
                  confirmDistanceFraction = confirmDistanceFraction,
                  hasPrevious = hasPrevious,
                  isLoadingPrevious = isLoadingPrevious,
              )
          triggerState = result.state
          if (result.shouldLoadPrevious) {
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
    confirmDistanceFraction: Float = 0.2f,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
    onLoadPrevious: () -> Unit,
) {
  AutoLoadPreviousEffectCore(
      key = listState,
      totalItems = totalItems,
      threshold = threshold,
      confirmDistanceFraction = confirmDistanceFraction,
      hasPrevious = hasPrevious,
      isLoadingPrevious = isLoadingPrevious,
      getSample = { listState.previousLoadScrollSample() },
      onLoadPrevious = onLoadPrevious,
  )
}

@Composable
fun AutoLoadPreviousEffect(
    gridState: LazyGridState,
    totalItems: Int,
    threshold: Int = 3,
    confirmDistanceFraction: Float = 0.2f,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
    onLoadPrevious: () -> Unit,
) {
  AutoLoadPreviousEffectCore(
      key = gridState,
      totalItems = totalItems,
      threshold = threshold,
      confirmDistanceFraction = confirmDistanceFraction,
      hasPrevious = hasPrevious,
      isLoadingPrevious = isLoadingPrevious,
      getSample = { gridState.previousLoadScrollSample() },
      onLoadPrevious = onLoadPrevious,
  )
}

internal data class PreviousLoadScrollSample(
    val firstVisibleIndex: Int?,
    val firstVisibleScrollOffset: Int,
    val viewportHeightPx: Int,
    val averageItemMainAxisSizePx: Float,
    val isScrollInProgress: Boolean,
) {
  val approximateDistanceFromStartPx: Float?
    get() =
        firstVisibleIndex?.let {
          it.coerceAtLeast(0) * averageItemMainAxisSizePx.coerceAtLeast(1f) +
              firstVisibleScrollOffset.coerceAtLeast(0)
        }
}

internal data class PreviousLoadTriggerState(
    val lastDistanceFromStartPx: Float? = null,
    val accumulatedTowardStartPx: Float = 0f,
    val triggeredInCurrentScroll: Boolean = false,
)

internal data class PreviousLoadTriggerResult(
    val state: PreviousLoadTriggerState,
    val shouldLoadPrevious: Boolean,
)

internal fun updatePreviousLoadTriggerState(
    state: PreviousLoadTriggerState,
    sample: PreviousLoadScrollSample,
    totalItems: Int,
    threshold: Int,
    confirmDistanceFraction: Float,
    hasPrevious: Boolean,
    isLoadingPrevious: Boolean,
): PreviousLoadTriggerResult {
  val distanceFromStart = sample.approximateDistanceFromStartPx
  val shouldResetGesture =
      !sample.isScrollInProgress ||
          sample.firstVisibleIndex == null ||
          sample.firstVisibleIndex > threshold ||
          totalItems <= 0 ||
          !hasPrevious
  if (shouldResetGesture) {
    return PreviousLoadTriggerResult(
        state =
            PreviousLoadTriggerState(
                lastDistanceFromStartPx = distanceFromStart,
                accumulatedTowardStartPx = 0f,
                triggeredInCurrentScroll = false,
            ),
        shouldLoadPrevious = false,
    )
  }

  val previousDistance = state.lastDistanceFromStartPx
  val movementTowardStart =
      if (previousDistance != null && distanceFromStart != null) {
        (previousDistance - distanceFromStart).coerceAtLeast(0f)
      } else {
        0f
      }
  val movedAwayFromStart =
      previousDistance != null && distanceFromStart != null && distanceFromStart > previousDistance
  val accumulated =
      if (movedAwayFromStart) 0f else state.accumulatedTowardStartPx + movementTowardStart
  val triggeredInCurrentScroll = if (movedAwayFromStart) false else state.triggeredInCurrentScroll
  val confirmDistancePx =
      sample.viewportHeightPx.coerceAtLeast(1) * confirmDistanceFraction.coerceAtLeast(0f)
  val shouldLoad =
      !isLoadingPrevious &&
          !triggeredInCurrentScroll &&
          accumulated >= confirmDistancePx &&
          distanceFromStart != null
  return PreviousLoadTriggerResult(
      state =
          PreviousLoadTriggerState(
              lastDistanceFromStartPx = distanceFromStart,
              accumulatedTowardStartPx = accumulated,
              triggeredInCurrentScroll = triggeredInCurrentScroll || shouldLoad,
          ),
      shouldLoadPrevious = shouldLoad,
  )
}

private fun LazyListState.previousLoadScrollSample(): PreviousLoadScrollSample {
  val items = layoutInfo.visibleItemsInfo
  val averageItemSize = items.map { it.size }.average().takeIf { !it.isNaN() }?.toFloat() ?: 1f
  return PreviousLoadScrollSample(
      firstVisibleIndex = items.firstOrNull()?.index,
      firstVisibleScrollOffset = firstVisibleItemScrollOffset,
      viewportHeightPx = layoutInfo.viewportSize.height,
      averageItemMainAxisSizePx = averageItemSize,
      isScrollInProgress = isScrollInProgress,
  )
}

private fun LazyGridState.previousLoadScrollSample(): PreviousLoadScrollSample {
  val items = layoutInfo.visibleItemsInfo
  val averageItemSize =
      items.map { it.size.height }.average().takeIf { !it.isNaN() }?.toFloat() ?: 1f
  return PreviousLoadScrollSample(
      firstVisibleIndex = items.firstOrNull()?.index,
      firstVisibleScrollOffset = firstVisibleItemScrollOffset,
      viewportHeightPx = layoutInfo.viewportSize.height,
      averageItemMainAxisSizePx = averageItemSize,
      isScrollInProgress = isScrollInProgress,
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
