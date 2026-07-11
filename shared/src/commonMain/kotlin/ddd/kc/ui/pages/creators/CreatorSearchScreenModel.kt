package ddd.kc.ui.pages.creators

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.key
import ddd.kc.data.remote.network.asException
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.ui.components.state.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.state.PaginationReducer
import ddd.kc.ui.components.state.PaginationSnapshot
import ddd.kc.utils.logging.KcLog
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.order_asc
import kc.shared.generated.resources.order_desc
import kc.shared.generated.resources.sort_alphabetical
import kc.shared.generated.resources.sort_date_indexed
import kc.shared.generated.resources.sort_date_updated
import kc.shared.generated.resources.sort_popularity
import kc.shared.generated.resources.sort_service_name
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

enum class CreatorSort(val apiValue: String) {
  FAVORITED("favorited"),
  INDEXED("indexed"),
  UPDATED("updated"),
  NAME("name"),
  SERVICE("service"),
}

enum class SortOrder(val apiValue: String) {
  DESC("desc"),
  ASC("asc"),
}

@Composable
fun creatorSortLabel(sort: CreatorSort): String =
    when (sort) {
      CreatorSort.FAVORITED -> stringResource(Res.string.sort_popularity)
      CreatorSort.INDEXED -> stringResource(Res.string.sort_date_indexed)
      CreatorSort.UPDATED -> stringResource(Res.string.sort_date_updated)
      CreatorSort.NAME -> stringResource(Res.string.sort_alphabetical)
      CreatorSort.SERVICE -> stringResource(Res.string.sort_service_name)
    }

@Composable
fun sortOrderLabel(order: SortOrder): String =
    when (order) {
      SortOrder.DESC -> stringResource(Res.string.order_desc)
      SortOrder.ASC -> stringResource(Res.string.order_asc)
    }

private val log = KcLog.withTag("CreatorSearchScreenModel")
private const val PAGE_SIZE = DEFAULT_PAGE_SIZE

data class CreatorSearchState(
    val query: String = "",
    val selectedService: String? = null,
    val sortBy: CreatorSort = CreatorSort.FAVORITED,
    val sortOrder: SortOrder = SortOrder.DESC,
    val allCreators: List<Creator> = emptyList(),
    val filteredCount: Int = 0,
    val paging: PaginationSnapshot<Creator> = PaginationSnapshot(loading = true),
) {
  val creators
    get() = paging.items

  val result
    get() =
        ddd.kc.data.model.QueryState<Unit>(
            isLoading = paging.loading,
            isRefreshing = paging.refreshing,
            error = paging.error,
        )

  val isLoading
    get() = paging.loading

  val error
    get() = paging.error

  val hasMore
    get() = paging.hasMore

  val canAutoLoadPrevious
    get() = paging.canAutoLoadPrevious

  val visiblePageInfo
    get() = paging.visiblePageInfo

  val isLoadingPrevious
    get() = paging.isLoadingPrevious

  val isLoadingMore
    get() = paging.isLoadingMore
}

class CreatorSearchScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<CreatorSearchState>(CreatorSearchState()) {
  private val reducer = PaginationReducer<Creator, CreatorKey> { it.key }
  private var searchJob: Job? = null

  fun init() {
    if (mutableState.value.creators.isEmpty()) reload(delayMs = 0)
  }

  fun onQueryChanged(query: String) {
    mutableState.value = mutableState.value.copy(query = query)
    reload()
  }

  fun onServiceChanged(service: String?) {
    mutableState.value = mutableState.value.copy(selectedService = service)
    reload(delayMs = 100)
  }

  fun onSortChanged(sort: CreatorSort) {
    mutableState.value = mutableState.value.copy(sortBy = sort)
    reload(delayMs = 100)
  }

  fun onSortOrderChanged(order: SortOrder) {
    mutableState.value = mutableState.value.copy(sortOrder = order)
    reload(delayMs = 100)
  }

  fun refresh() {
    reload(delayMs = 0, forceRefresh = true)
  }

  fun loadMore() {
    val state = mutableState.value
    if (!reducer.canLoadMore(state.paging)) return
    val filtered = currentFilteredCreators(state)
    val offset = state.paging.offset
    val nextItems = filtered.drop(offset).take(PAGE_SIZE)
    mutableState.value = state.copy(paging = reducer.beginAppend(state.paging))
    updatePaging(
        reducer.reduceAppend(
            mutableState.value.paging,
            nextItems,
            offset + nextItems.size < filtered.size,
            offset + nextItems.size,
        )
    )
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (!reducer.canLoadPrevious(state.paging)) return
    val filtered = currentFilteredCreators(state)
    val previousOffset = (state.paging.startOffset - PAGE_SIZE).coerceAtLeast(0)
    val previousItems =
        filtered.drop(previousOffset).take(state.paging.startOffset - previousOffset)
    mutableState.value = state.copy(paging = reducer.beginPrepend(state.paging))
    updatePaging(
        reducer.reducePrepend(
            mutableState.value.paging,
            previousItems,
            mutableState.value.paging.hasMore,
            previousOffset,
        )
    )
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    val targetPage = page.coerceIn(1, state.paging.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    replaceWindow((targetPage - 1) * PAGE_SIZE)
  }

  fun onVisibleCreatorIndex(firstVisibleCreatorIndex: Int) {
    updatePaging(
        reducer.updateVisiblePage(mutableState.value.paging, firstVisibleCreatorIndex, PAGE_SIZE)
    )
  }

  private fun reload(delayMs: Long = 300, forceRefresh: Boolean = false) {
    searchJob?.cancel()
    searchJob =
        screenModelScope.launch {
          try {
            if (delayMs > 0) delay(delayMs)
            val state = mutableState.value
            updatePaging(reducer.beginLoad(state.paging, forceRefresh))
            creatorRepo.observeCreators(forceRefresh).collect { next ->
              val allCreators = next.data ?: mutableState.value.allCreators
              val rows =
                  filterCreators(
                      allCreators,
                      state.query,
                      state.selectedService,
                      state.sortBy,
                      state.sortOrder,
                  )
              log.i { "创作者搜索 -> 状态(queryLength=${state.query.length},count=${rows.size})" }
              if (next.data != null) {
                mutableState.value =
                    pageStateFor(
                        mutableState.value.copy(allCreators = allCreators),
                        filtered = rows,
                        offset = 0,
                    )
              }
              next.error?.let {
                updatePaging(
                    reducer.reduceFirstPageError(mutableState.value.paging, it.asException())
                )
              }
              if (!next.isLoading && !next.isRefreshing) {
                searchJob = null
              }
            }
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }

  private fun replaceWindow(offset: Int) {
    val state = mutableState.value
    val filtered = currentFilteredCreators(state)
    mutableState.value = pageStateFor(state, filtered, offset)
  }

  private fun currentFilteredCreators(state: CreatorSearchState): List<Creator> =
      filterCreators(
          state.allCreators,
          state.query,
          state.selectedService,
          state.sortBy,
          state.sortOrder,
      )

  private fun pageStateFor(
      state: CreatorSearchState,
      filtered: List<Creator>,
      offset: Int,
  ): CreatorSearchState {
    val safeOffset = offset.coerceIn(0, lastOffsetForCount(filtered.size))
    val pageItems = filtered.drop(safeOffset).take(PAGE_SIZE)
    return state.copy(
        filteredCount = filtered.size,
        paging =
            reducer.reduceFirstPage(
                state.paging,
                pageItems,
                safeOffset + pageItems.size < filtered.size,
                safeOffset + pageItems.size,
                pageInfoForCount(filtered.size, safeOffset),
                safeOffset,
            ),
    )
  }

  private fun updatePaging(paging: PaginationSnapshot<Creator>) {
    mutableState.value = mutableState.value.copy(paging = paging)
  }
}

private fun pageInfoForCount(count: Int, currentOffset: Int): PageInfo? {
  if (count <= PAGE_SIZE) return null
  val lastOffset = lastOffsetForCount(count)
  val currentPage = currentOffset / PAGE_SIZE + 1
  val lastPage = lastOffset / PAGE_SIZE + 1
  return PageInfo(
      currentPage = currentPage.coerceIn(1, lastPage),
      lastPage = lastPage,
      currentOffset = currentOffset.coerceAtLeast(0),
      lastOffset = lastOffset,
  )
}

private fun lastOffsetForCount(count: Int): Int =
    if (count <= 0) 0 else ((count - 1) / PAGE_SIZE) * PAGE_SIZE

private fun filterCreators(
    creators: List<Creator>,
    query: String,
    service: String?,
    sortBy: CreatorSort,
    order: SortOrder,
): List<Creator> {
  val normalizedQuery = query.trim()
  val comparator =
      when (sortBy) {
        CreatorSort.FAVORITED -> compareBy<Creator> { it.favorited }
        CreatorSort.INDEXED -> compareBy { it.indexed }
        CreatorSort.UPDATED -> compareBy { it.updated }
        CreatorSort.NAME -> compareBy { it.name.lowercase() }
        CreatorSort.SERVICE ->
            compareBy<Creator> { it.service.lowercase() }.thenBy { it.name.lowercase() }
      }
  val filtered =
      creators
          .asSequence()
          .filter { service == null || it.service.equals(service, ignoreCase = true) }
          .filter {
            normalizedQuery.isBlank() ||
                it.name.contains(normalizedQuery, ignoreCase = true) ||
                it.id.contains(normalizedQuery, ignoreCase = true) ||
                it.publicId?.contains(normalizedQuery, ignoreCase = true) == true
          }
          .toList()
  return filtered.sortedWith(if (order == SortOrder.ASC) comparator else comparator.reversed())
}
