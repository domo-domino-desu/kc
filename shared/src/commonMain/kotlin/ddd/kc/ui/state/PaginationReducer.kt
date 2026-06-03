package ddd.kc.ui.state

data class PaginationSnapshot<Item>(
    val items: List<Item> = emptyList(),
    val offset: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val appendErrorMessage: String? = null,
)

class PaginationReducer<Item, Key>(private val keyOf: (Item) -> Key) {

  fun beginLoad(
      snapshot: PaginationSnapshot<Item>,
      forceRefresh: Boolean,
  ): PaginationSnapshot<Item> {
    val hasExisting = snapshot.items.isNotEmpty()
    return snapshot.copy(
        loading = !hasExisting,
        refreshing = hasExisting || forceRefresh,
        isLoadingMore = false,
        errorMessage = null,
        appendErrorMessage = null,
    )
  }

  fun canLoadMore(snapshot: PaginationSnapshot<Item>, force: Boolean = false): Boolean {
    if (!snapshot.hasMore) return false
    if (snapshot.isLoadingMore || snapshot.loading || snapshot.refreshing) return false
    if (!force && !snapshot.appendErrorMessage.isNullOrBlank()) return false
    return true
  }

  fun beginAppend(snapshot: PaginationSnapshot<Item>): PaginationSnapshot<Item> =
      snapshot.copy(isLoadingMore = true, appendErrorMessage = null)

  fun reduceFirstPage(
      snapshot: PaginationSnapshot<Item>,
      items: List<Item>,
      hasMore: Boolean,
      nextOffset: Int,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          items = items,
          offset = nextOffset,
          loading = false,
          refreshing = false,
          isLoadingMore = false,
          hasMore = hasMore,
          errorMessage = null,
          appendErrorMessage = null,
      )

  fun reduceFirstPageError(
      snapshot: PaginationSnapshot<Item>,
      error: Throwable,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          loading = false,
          refreshing = false,
          isLoadingMore = false,
          errorMessage = error.message ?: error.toString(),
      )

  fun reduceAppend(
      snapshot: PaginationSnapshot<Item>,
      items: List<Item>,
      hasMore: Boolean,
      nextOffset: Int,
  ): PaginationSnapshot<Item> {
    val merged = mergeByKey(snapshot.items, items)
    return snapshot.copy(
        items = merged,
        offset = nextOffset,
        isLoadingMore = false,
        hasMore = hasMore,
        appendErrorMessage = null,
    )
  }

  fun reduceAppendError(
      snapshot: PaginationSnapshot<Item>,
      error: Throwable,
  ): PaginationSnapshot<Item> =
      snapshot.copy(
          isLoadingMore = false,
          appendErrorMessage = error.message ?: error.toString(),
      )

  private fun mergeByKey(existing: List<Item>, incoming: List<Item>): List<Item> {
    if (incoming.isEmpty()) return existing
    val map = LinkedHashMap<Key, Item>(existing.size + incoming.size)
    existing.forEach { map[keyOf(it)] = it }
    incoming.forEach { map[keyOf(it)] = it }
    return map.values.toList()
  }
}
