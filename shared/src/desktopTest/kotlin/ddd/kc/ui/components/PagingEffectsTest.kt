package ddd.kc.ui.components

import ddd.kc.data.model.PageInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagingEffectsTest {
  @Test
  fun repeatSelectionRefreshesOnlyAtTopOfFirstPage() {
    assertTrue(
        shouldRefreshOnRepeatSelection(
            isAtTop = true,
            visiblePageInfo =
                PageInfo(currentPage = 1, lastPage = 3, currentOffset = 0, lastOffset = 100),
        )
    )

    assertFalse(
        shouldRefreshOnRepeatSelection(
            isAtTop = false,
            visiblePageInfo =
                PageInfo(currentPage = 1, lastPage = 3, currentOffset = 0, lastOffset = 100),
        )
    )

    assertFalse(
        shouldRefreshOnRepeatSelection(
            isAtTop = true,
            visiblePageInfo =
                PageInfo(currentPage = 2, lastPage = 3, currentOffset = 50, lastOffset = 100),
        )
    )
  }

  @Test
  fun previousLoadWaitsForConfirmationDistance() {
    val result =
        updatePreviousLoadTriggerState(
            state = PreviousLoadTriggerState(lastDistanceFromStartPx = 250f),
            sample =
                PreviousLoadScrollSample(
                    firstVisibleIndex = 1,
                    firstVisibleScrollOffset = 100,
                    viewportHeightPx = 1000,
                    averageItemMainAxisSizePx = 100f,
                    isScrollInProgress = true,
                ),
            totalItems = 50,
            threshold = 3,
            confirmDistanceFraction = 0.2f,
            hasPrevious = true,
            isLoadingPrevious = false,
        )

    assertFalse(result.shouldLoadPrevious)
  }

  @Test
  fun previousLoadTriggersOnlyOncePerScrollGesture() {
    val first =
        updatePreviousLoadTriggerState(
            state = PreviousLoadTriggerState(lastDistanceFromStartPx = 500f),
            sample =
                PreviousLoadScrollSample(
                    firstVisibleIndex = 3,
                    firstVisibleScrollOffset = 0,
                    viewportHeightPx = 1000,
                    averageItemMainAxisSizePx = 100f,
                    isScrollInProgress = true,
                ),
            totalItems = 50,
            threshold = 3,
            confirmDistanceFraction = 0.2f,
            hasPrevious = true,
            isLoadingPrevious = false,
        )
    val second =
        updatePreviousLoadTriggerState(
            state = first.state,
            sample =
                PreviousLoadScrollSample(
                    firstVisibleIndex = 2,
                    firstVisibleScrollOffset = 0,
                    viewportHeightPx = 1000,
                    averageItemMainAxisSizePx = 100f,
                    isScrollInProgress = true,
                ),
            totalItems = 50,
            threshold = 3,
            confirmDistanceFraction = 0.2f,
            hasPrevious = true,
            isLoadingPrevious = false,
        )

    assertTrue(first.shouldLoadPrevious)
    assertFalse(second.shouldLoadPrevious)
  }

  @Test
  fun previousLoadRearmsAfterScrollEnds() {
    val triggered =
        PreviousLoadTriggerState(
            lastDistanceFromStartPx = 300f,
            accumulatedTowardStartPx = 200f,
            triggeredInCurrentScroll = true,
        )
    val reset =
        updatePreviousLoadTriggerState(
            state = triggered,
            sample =
                PreviousLoadScrollSample(
                    firstVisibleIndex = 3,
                    firstVisibleScrollOffset = 0,
                    viewportHeightPx = 1000,
                    averageItemMainAxisSizePx = 100f,
                    isScrollInProgress = false,
                ),
            totalItems = 50,
            threshold = 3,
            confirmDistanceFraction = 0.2f,
            hasPrevious = true,
            isLoadingPrevious = false,
        )
    val next =
        updatePreviousLoadTriggerState(
            state = reset.state.copy(lastDistanceFromStartPx = 500f),
            sample =
                PreviousLoadScrollSample(
                    firstVisibleIndex = 3,
                    firstVisibleScrollOffset = 0,
                    viewportHeightPx = 1000,
                    averageItemMainAxisSizePx = 100f,
                    isScrollInProgress = true,
                ),
            totalItems = 50,
            threshold = 3,
            confirmDistanceFraction = 0.2f,
            hasPrevious = true,
            isLoadingPrevious = false,
        )

    assertFalse(reset.shouldLoadPrevious)
    assertTrue(next.shouldLoadPrevious)
  }
}
