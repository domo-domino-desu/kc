package ddd.kc.ui.state

import ddd.kc.data.network.PageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaginationReducerTest {
  private val reducer = PaginationReducer<Int, Int> { it }

  @Test
  fun visiblePageFollowsLoadedWindowAfterJumpAppendAndPrepend() {
    val jumped =
        reducer.reduceFirstPage(
            snapshot = PaginationSnapshot(),
            items = (150 until 200).toList(),
            hasMore = true,
            nextOffset = 200,
            pageInfo =
                PageInfo(
                    currentPage = 4,
                    lastPage = 10,
                    currentOffset = 150,
                    lastOffset = 450,
                ),
        )

    assertEquals(4, jumped.visiblePageInfo?.currentPage)
    assertTrue(jumped.canAutoLoadPrevious)

    val appended =
        reducer.reduceAppend(
            snapshot = jumped,
            items = (200 until 250).toList(),
            hasMore = true,
            nextOffset = 250,
            pageInfo = null,
        )
    val page5 = reducer.updateVisiblePage(appended, firstVisibleItemIndex = 50, pageSize = 50)

    assertEquals(5, page5.visiblePageInfo?.currentPage)
    assertEquals(200, page5.visiblePageInfo?.currentOffset)
    assertTrue(page5.canAutoLoadPrevious)

    val prepended =
        reducer.reducePrepend(
            snapshot = page5,
            items = (100 until 150).toList(),
            hasMore = true,
            startOffset = 100,
            pageInfo = null,
        )
    val page3 = reducer.updateVisiblePage(prepended, firstVisibleItemIndex = 0, pageSize = 50)

    assertEquals(3, page3.visiblePageInfo?.currentPage)
    assertEquals(100, page3.visiblePageInfo?.currentOffset)
  }

  @Test
  fun beginJumpKeepsPreviousAvailabilityButLoadingStillBlocksRequests() {
    val page4 =
        reducer.reduceFirstPage(
            snapshot = PaginationSnapshot(),
            items = (150 until 200).toList(),
            hasMore = true,
            nextOffset = 200,
            pageInfo =
                PageInfo(
                    currentPage = 4,
                    lastPage = 10,
                    currentOffset = 150,
                    lastOffset = 450,
                ),
        )
    val page4And5 =
        reducer
            .reduceAppend(page4, (200 until 250).toList(), hasMore = true, nextOffset = 250)
            .let { reducer.updateVisiblePage(it, firstVisibleItemIndex = 50, pageSize = 50) }

    assertTrue(page4And5.canAutoLoadPrevious)
    assertTrue(reducer.canLoadPrevious(page4And5))

    val jumpingToPage4 = reducer.beginJump(page4And5, targetOffset = 150)

    assertEquals(4, jumpingToPage4.visiblePageInfo?.currentPage)
    assertTrue(jumpingToPage4.canAutoLoadPrevious)
    assertFalse(reducer.canLoadPrevious(jumpingToPage4))
  }

  @Test
  fun jumpErrorRestoresPreviousWindow() {
    val previous =
        reducer.reduceFirstPage(
            snapshot = PaginationSnapshot(),
            items = (150 until 250).toList(),
            hasMore = true,
            nextOffset = 250,
            pageInfo =
                PageInfo(
                    currentPage = 4,
                    lastPage = 10,
                    currentOffset = 150,
                    lastOffset = 450,
                ),
        )
    val jumping = reducer.beginJump(previous, targetOffset = 300)

    val restored = reducer.reduceJumpError(previous, IllegalStateException("failed"))

    assertEquals(previous.items, restored.items)
    assertEquals(150, restored.startOffset)
    assertEquals(150, restored.visibleOffset)
    assertEquals(4, restored.visiblePageInfo?.currentPage)
    assertEquals("failed", restored.errorMessage)
    assertFalse(restored.loading)
    assertFalse(restored.refreshing)
    assertEquals(300, jumping.startOffset)
  }
}
