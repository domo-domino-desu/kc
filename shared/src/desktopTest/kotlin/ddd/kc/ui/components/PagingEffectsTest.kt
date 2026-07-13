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
}
