package ddd.kc.ui.components.state

import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.QueryError
import ddd.kc.data.remote.network.toQueryError

data class PaginationSnapshot<Item>(
    val items: List<Item> = emptyList(),
    val startOffset: Int = 0,
    val offset: Int = 0,
    val pageInfo: PageInfo? = null,
    val visibleOffset: Int = startOffset,
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
    get() = pageInfoForOffset(pageInfo, visibleOffset, DEFAULT_PAGE_SIZE)
}

const val DEFAULT_PAGE_SIZE = 50

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

class PaginationReducer<Item, Key>(private val keyOf: (Item) -> Key) {

  fun beginLoad(
      snapshot: PaginationSnapshot<Item>,
      forceRefresh: Boolean,
  ): PaginationSnapshot<Item> {
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

  fun canLoadMore(snapshot: PaginationSnapshot<Item>, force: Boolean = false): Boolean {
    if (!snapshot.hasMore) return false
    if (snapshot.isLoadingMore || snapshot.loading || snapshot.refreshing) return false
    if (!force && snapshot.appendError != null) return false
    return true
  }

  fun canLoadPrevious(snapshot: PaginationSnapshot<Item>, force: Boolean = false): Boolean {
    if (!snapshot.canAutoLoadPrevious) return false
    if (
        snapshot.isLoadingPrevious ||
            snapshot.isLoadingMore ||
            snapshot.loading ||
            snapshot.refreshing
    )
        return false
    if (!force && snapshot.prependError != null) return false
    return true
  }

  fun beginJump(
      snapshot: PaginationSnapshot<Item>,
      targetOffset: Int,
  ): PaginationSnapshot<Item> {
    val hasExisting = snapshot.items.isNotEmpty()
    return snapshot.copy(
        startOffset = targetOffset.coerceAtLeast(0),
        visibleOffset = targetOffset.coerceAtLeast(0),
        loading = !hasExisting,
        refreshing = hasExisting,
        isLoadingPrevious = false,
        isLoadingMore = false,
        error = null,
        prependError = null,
        appendError = null,
    )
  }

  fun beginPrepend(snapshot: PaginationSnapshot<Item>): PaginationSnapshot<Item> =
      snapshot.copy(isLoadingPrevious = true, prependError = null)

  fun beginAppend(snapshot: PaginationSnapshot<Item>): PaginationSnapshot<Item> =
      snapshot.copy(isLoadingMore = true, appendError = null)

  fun reduceFirstPage(
      snapshot: PaginationSnapshot<Item>,
      items: List<Item>,
      hasMore: Boolean,
      nextOffset: Int,
      pageInfo: PageInfo? = null,
      startOffset: Int = pageInfo?.currentOffset ?: 0,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          items = items,
          startOffset = startOffset,
          offset = nextOffset,
          pageInfo = pageInfo,
          visibleOffset = startOffset,
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
      snapshot: PaginationSnapshot<Item>,
      error: Throwable,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          loading = false,
          refreshing = false,
          isLoadingPrevious = false,
          isLoadingMore = false,
          error = error.toQueryError(),
      )

  fun reduceJumpError(
      previous: PaginationSnapshot<Item>,
      error: Throwable,
  ): PaginationSnapshot<Item> =
      previous.copy(
          loading = false,
          refreshing = false,
          isLoadingPrevious = false,
          isLoadingMore = false,
          error = error.toQueryError(),
      )

  fun reducePrepend(
      snapshot: PaginationSnapshot<Item>,
      items: List<Item>,
      hasMore: Boolean,
      startOffset: Int,
      pageInfo: PageInfo? = null,
  ): PaginationSnapshot<Item> {
    val merged = mergeByKey(items, snapshot.items)
    return snapshot.copy(
        items = merged,
        startOffset = startOffset,
        pageInfo = pageInfo ?: snapshot.pageInfo,
        visibleOffset = snapshot.visibleOffset,
        isLoadingPrevious = false,
        hasMore = hasMore,
        prependError = null,
    )
  }

  fun reduceAppend(
      snapshot: PaginationSnapshot<Item>,
      items: List<Item>,
      hasMore: Boolean,
      nextOffset: Int,
      pageInfo: PageInfo? = null,
  ): PaginationSnapshot<Item> {
    val merged = mergeByKey(snapshot.items, items)
    return snapshot.copy(
        items = merged,
        offset = nextOffset,
        pageInfo = pageInfo ?: snapshot.pageInfo,
        isLoadingMore = false,
        hasMore = hasMore,
        appendError = null,
    )
  }

  fun reduceAppendError(
      snapshot: PaginationSnapshot<Item>,
      error: Throwable,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          isLoadingMore = false,
          appendError = error.toQueryError(),
      )

  fun reducePrependError(
      snapshot: PaginationSnapshot<Item>,
      error: Throwable,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          isLoadingPrevious = false,
          prependError = error.toQueryError(),
      )

  fun updateVisiblePage(
      snapshot: PaginationSnapshot<Item>,
      firstVisibleItemIndex: Int,
      pageSize: Int,
  ): PaginationSnapshot<Item> {
    val info = snapshot.pageInfo ?: return snapshot
    if (snapshot.items.isEmpty() || pageSize <= 0) return snapshot
    val relativeIndex =
        firstVisibleItemIndex.coerceAtLeast(0).coerceAtMost(snapshot.items.lastIndex)
    val absoluteOffset = snapshot.startOffset + relativeIndex
    if (snapshot.visibleOffset == absoluteOffset) return snapshot
    return snapshot.copy(visibleOffset = absoluteOffset)
  }

  private fun mergeByKey(existing: List<Item>, incoming: List<Item>): List<Item> {
    if (incoming.isEmpty()) return existing
    val map = LinkedHashMap<Key, Item>(existing.size + incoming.size)
    existing.forEach { map[keyOf(it)] = it }
    incoming.forEach { map[keyOf(it)] = it }
    return map.values.toList()
  }
}
