package ddd.kc.ui.components.paging

import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.QueryError
import ddd.kc.data.remote.network.toQueryError

const val DEFAULT_PAGE_SIZE = 50

data class OffsetPagingState<Item>(
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val items: List<Item> = emptyList(),
    val startOffset: Int = 0,
    val offset: Int = 0,
    val pageInfo: PageInfo? = null,
    val visibleOffset: Int = startOffset,
    val currentPage: Int = pageInfo?.currentPage ?: 1,
    val lastPage: Int? = pageInfo?.lastPage,
    val viewport: PagingAnchor = PagingAnchor(),
    val navigationEffect: PagingEffect? = null,
    val transactionId: Long = 0L,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val isLoadingPrevious: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: QueryError? = null,
    val prependError: QueryError? = null,
    val appendError: QueryError? = null,
) {
  val hasPrevious: Boolean
    get() = startOffset > 0

  val canAutoLoadPrevious: Boolean
    get() = hasPrevious

  val visiblePageInfo: PageInfo?
    get() {
      val info = pageInfo ?: return null
      val page = currentPage.coerceIn(1, lastPage ?: info.lastPage)
      return info.copy(
          currentPage = page,
          currentOffset = ((page - 1) * pageSize).coerceIn(0, info.lastOffset),
      )
    }

  fun normalizedForRestore(): OffsetPagingState<Item> =
      copy(
          loading = false,
          refreshing = false,
          isLoadingPrevious = false,
          isLoadingMore = false,
      )
}

fun pageInfoForOffset(
    pageInfo: PageInfo?,
    visibleOffset: Int,
    pageSize: Int = DEFAULT_PAGE_SIZE,
): PageInfo? {
  val info = pageInfo ?: return null
  if (pageSize <= 0) return info
  val currentPage = ((visibleOffset.coerceAtLeast(0) / pageSize) + 1).coerceIn(1, info.lastPage)
  val currentOffset = ((currentPage - 1) * pageSize).coerceIn(0, info.lastOffset)
  if (info.currentPage == currentPage && info.currentOffset == currentOffset) return info
  return info.copy(currentPage = currentPage, currentOffset = currentOffset)
}

class OffsetPagingMachine<Item, Key>(private val keyOf: (Item) -> Key) {

  fun beginLoad(
      snapshot: OffsetPagingState<Item>,
      forceRefresh: Boolean,
  ): OffsetPagingState<Item> {
    val hasExisting = snapshot.items.isNotEmpty()
    return snapshot.copy(
        loading = !hasExisting,
        refreshing = hasExisting || forceRefresh,
        isLoadingPrevious = false,
        isLoadingMore = false,
        error = null,
        prependError = null,
        appendError = null,
    )
  }

  fun canLoadMore(snapshot: OffsetPagingState<Item>, force: Boolean = false): Boolean {
    if (!snapshot.hasMore) return false
    if (snapshot.isLoadingMore || snapshot.loading || snapshot.refreshing) return false
    if (!force && snapshot.appendError != null) return false
    return true
  }

  fun canLoadPrevious(snapshot: OffsetPagingState<Item>): Boolean {
    if (!snapshot.canAutoLoadPrevious) return false
    if (
        snapshot.isLoadingPrevious ||
            snapshot.isLoadingMore ||
            snapshot.loading ||
            snapshot.refreshing
    )
        return false
    return true
  }

  fun beginJump(
      snapshot: OffsetPagingState<Item>,
      targetOffset: Int,
  ): OffsetPagingState<Item> {
    val transactionId = snapshot.transactionId + 1
    val targetPage = targetOffset.coerceAtLeast(0) / snapshot.pageSize + 1
    return snapshot.copy(
        items = emptyList(),
        startOffset = targetOffset.coerceAtLeast(0),
        offset = targetOffset.coerceAtLeast(0),
        visibleOffset = targetOffset.coerceAtLeast(0),
        currentPage = targetPage,
        loading = true,
        refreshing = false,
        isLoadingPrevious = false,
        isLoadingMore = false,
        error = null,
        prependError = null,
        appendError = null,
        viewport = PagingAnchor(),
        navigationEffect = PagingEffect.ScrollToTop(transactionId),
        transactionId = transactionId,
    )
  }

  fun beginPrepend(snapshot: OffsetPagingState<Item>): OffsetPagingState<Item> =
      snapshot.copy(isLoadingPrevious = true, prependError = null)

  fun beginAppend(snapshot: OffsetPagingState<Item>): OffsetPagingState<Item> =
      snapshot.copy(isLoadingMore = true, appendError = null)

  fun consumeNavigationEffect(
      snapshot: OffsetPagingState<Item>,
      transactionId: Long,
  ): OffsetPagingState<Item> =
      if (snapshot.navigationEffect?.transactionId == transactionId) {
        snapshot.copy(navigationEffect = null)
      } else {
        snapshot
      }

  fun reduceFirstPage(
      snapshot: OffsetPagingState<Item>,
      items: List<Item>,
      hasMore: Boolean,
      nextOffset: Int,
      pageInfo: PageInfo? = null,
      startOffset: Int = pageInfo?.currentOffset ?: 0,
  ): OffsetPagingState<Item> =
      snapshot.copy(
          items = items,
          startOffset = startOffset,
          offset = nextOffset,
          pageInfo = pageInfo,
          visibleOffset = startOffset,
          currentPage = pageInfo?.currentPage ?: snapshot.currentPage,
          lastPage = pageInfo?.lastPage ?: snapshot.lastPage,
          loading = false,
          refreshing = false,
          isLoadingPrevious = false,
          isLoadingMore = false,
          hasMore = hasMore,
          error = null,
          prependError = null,
          appendError = null,
      )

  fun reduceFirstPageError(
      snapshot: OffsetPagingState<Item>,
      error: Throwable,
  ): OffsetPagingState<Item> =
      snapshot.copy(
          loading = false,
          refreshing = false,
          isLoadingPrevious = false,
          isLoadingMore = false,
          error = error.toQueryError(),
      )

  fun reduceJumpError(
      previous: OffsetPagingState<Item>,
      error: Throwable,
  ): OffsetPagingState<Item> =
      previous.copy(
          loading = false,
          refreshing = false,
          isLoadingPrevious = false,
          isLoadingMore = false,
          error = error.toQueryError(),
          transactionId = previous.transactionId + 1,
          navigationEffect =
              PagingEffect.RestoreViewport(
                  previous.viewport,
                  previous.transactionId + 1,
              ),
      )

  fun reducePrepend(
      snapshot: OffsetPagingState<Item>,
      items: List<Item>,
      hasMore: Boolean,
      startOffset: Int,
      pageInfo: PageInfo? = null,
  ): OffsetPagingState<Item> {
    val merged = mergeByKey(items, snapshot.items)
    return snapshot.copy(
        items = merged,
        startOffset = startOffset,
        pageInfo = pageInfo ?: snapshot.pageInfo,
        lastPage = pageInfo?.lastPage ?: snapshot.lastPage,
        visibleOffset = snapshot.visibleOffset,
        isLoadingPrevious = false,
        hasMore = hasMore,
        prependError = null,
    )
  }

  fun reduceAppend(
      snapshot: OffsetPagingState<Item>,
      items: List<Item>,
      hasMore: Boolean,
      nextOffset: Int,
      pageInfo: PageInfo? = null,
  ): OffsetPagingState<Item> {
    val merged = mergeByKey(snapshot.items, items)
    return snapshot.copy(
        items = merged,
        offset = nextOffset,
        pageInfo = pageInfo ?: snapshot.pageInfo,
        lastPage = pageInfo?.lastPage ?: snapshot.lastPage,
        isLoadingMore = false,
        hasMore = hasMore,
        appendError = null,
    )
  }

  fun reduceAppendError(
      snapshot: OffsetPagingState<Item>,
      error: Throwable,
  ): OffsetPagingState<Item> =
      snapshot.copy(
          isLoadingMore = false,
          appendError = error.toQueryError(),
      )

  fun reducePrependError(
      snapshot: OffsetPagingState<Item>,
      error: Throwable,
  ): OffsetPagingState<Item> =
      snapshot.copy(
          isLoadingPrevious = false,
          prependError = error.toQueryError(),
      )

  fun updateVisiblePage(
      snapshot: OffsetPagingState<Item>,
      firstVisibleItemIndex: Int,
      pageSize: Int,
  ): OffsetPagingState<Item> {
    return updateViewport(snapshot, firstVisibleItemIndex, 0, null, pageSize)
  }

  fun updateViewport(
      snapshot: OffsetPagingState<Item>,
      firstVisibleItemIndex: Int,
      firstVisibleItemScrollOffset: Int,
      anchorKey: String?,
      pageSize: Int,
  ): OffsetPagingState<Item> {
    if (snapshot.items.isEmpty() || pageSize <= 0) return snapshot
    val relativeIndex = firstVisibleItemIndex.coerceIn(0, snapshot.items.lastIndex)
    val absoluteOffset = snapshot.startOffset + relativeIndex
    val page = absoluteOffset / pageSize + 1
    val viewport =
        PagingAnchor(anchorKey, relativeIndex, firstVisibleItemScrollOffset.coerceAtLeast(0))
    if (snapshot.visibleOffset == absoluteOffset && snapshot.viewport == viewport) return snapshot
    return snapshot.copy(visibleOffset = absoluteOffset, currentPage = page, viewport = viewport)
  }

  private fun mergeByKey(existing: List<Item>, incoming: List<Item>): List<Item> {
    if (incoming.isEmpty()) return existing
    val map = LinkedHashMap<Key, Item>(existing.size + incoming.size)
    existing.forEach { map[keyOf(it)] = it }
    incoming.forEach { map[keyOf(it)] = it }
    return map.values.toList()
  }
}
