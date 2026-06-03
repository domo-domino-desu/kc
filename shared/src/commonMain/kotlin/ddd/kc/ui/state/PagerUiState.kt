package ddd.kc.ui.state

data class PagerUiState<T>(
    val items: List<T> = emptyList(),
    val currentIndex: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
) {
  val currentItem: T?
    get() = items.getOrNull(currentIndex)
}

const val PAGER_NEXT_PREFETCH_COUNT = 3
const val PAGER_PREVIOUS_PREFETCH_COUNT = 1
const val PAGER_PREFETCH_DEBOUNCE_MS = 500L
