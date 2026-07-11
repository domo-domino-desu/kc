package ddd.kc.ui.pages.dm

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DM
import ddd.kc.data.model.DmKey
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.key
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.ui.components.state.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.state.PaginationReducer
import ddd.kc.ui.components.state.PaginationSnapshot
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("DmSearchScreenModel")
private const val PAGE_SIZE = DEFAULT_PAGE_SIZE

data class DmSearchState(
    val query: String = "",
    val paging: PaginationSnapshot<DM> = PaginationSnapshot(),
) {
  val dms
    get() = paging.items

  val result
    get() =
        QueryState<Unit>(
            isLoading = paging.loading,
            isRefreshing = paging.refreshing,
            error = paging.error,
        )

  val isLoading
    get() = paging.loading

  val error
    get() = paging.error

  val visiblePageInfo
    get() = paging.visiblePageInfo

  val canAutoLoadPrevious
    get() = paging.canAutoLoadPrevious

  val isLoadingPrevious
    get() = paging.isLoadingPrevious

  val isLoadingMore
    get() = paging.isLoadingMore

  val prependError
    get() = paging.prependError

  val appendError
    get() = paging.appendError

  val hasMore
    get() = paging.hasMore
}

class DmSearchScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<DmSearchState>(DmSearchState()) {
  private val reducer = PaginationReducer<DM, DmKey> { it.key }
  private var searchJob: Job? = null
  private var generation = 0L

  fun init() = Unit

  fun onQueryChanged(query: String) {
    searchJob?.cancel()
    generation++
    mutableState.value = DmSearchState(query = query)
    if (query.isBlank()) return
    val requestGeneration = generation
    searchJob =
        screenModelScope.launch {
          try {
            delay(300)
            loadFirstPage(query, forceRefresh = true, requestGeneration)
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }

  fun refresh() {
    val query = mutableState.value.query
    if (query.isBlank()) return
    searchJob?.cancel()
    val requestGeneration = ++generation
    searchJob = screenModelScope.launch { loadFirstPage(query, true, requestGeneration) }
  }

  fun loadMore() {
    val state = mutableState.value
    if (state.query.isBlank() || !reducer.canLoadMore(state.paging)) return
    screenModelScope.launch {
      updatePaging(reducer.beginAppend(mutableState.value.paging))
      fetchPage(state.query, mutableState.value.paging.offset)
    }
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (state.query.isBlank() || !reducer.canLoadPrevious(state.paging)) return
    val offset = (state.paging.startOffset - PAGE_SIZE).coerceAtLeast(0)
    screenModelScope.launch {
      updatePaging(reducer.beginPrepend(mutableState.value.paging))
      fetchPage(state.query, offset, prepend = true)
    }
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    if (state.query.isBlank()) return
    val targetPage = page.coerceIn(1, state.paging.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    searchJob?.cancel()
    val requestGeneration = ++generation
    val previous = state.paging
    updatePaging(reducer.beginJump(previous, offset))
    screenModelScope.launch {
      fetchPage(
          state.query,
          offset,
          replace = true,
          rollback = previous,
          requestGeneration = requestGeneration,
      )
    }
  }

  fun onVisibleItemIndex(firstVisibleItemIndex: Int) {
    updatePaging(
        reducer.updateVisiblePage(mutableState.value.paging, firstVisibleItemIndex, PAGE_SIZE)
    )
  }

  private suspend fun loadFirstPage(query: String, forceRefresh: Boolean, requestGeneration: Long) {
    updatePaging(reducer.beginLoad(mutableState.value.paging, forceRefresh))
    fetchPage(query, 0, replace = true, requestGeneration = requestGeneration)
  }

  private suspend fun fetchPage(
      query: String,
      offset: Int,
      replace: Boolean = false,
      prepend: Boolean = false,
      rollback: PaginationSnapshot<DM>? = null,
      requestGeneration: Long = generation,
  ) {
    resultOfSuspend { creatorRepo.searchDMsPage(query, offset, forceRefresh = true) }
        .onSuccess { page ->
          if (requestGeneration != generation || mutableState.value.query != query) return@onSuccess
          val hasMore = page.pageInfo?.hasNext ?: (page.items.size >= PAGE_SIZE)
          val next =
              when {
                replace ->
                    reducer.reduceFirstPage(
                        mutableState.value.paging,
                        page.items,
                        hasMore,
                        offset + page.items.size,
                        page.pageInfo,
                        offset,
                    )
                prepend ->
                    reducer.reducePrepend(
                        mutableState.value.paging,
                        page.items,
                        mutableState.value.paging.hasMore,
                        offset,
                    )
                else ->
                    reducer.reduceAppend(
                        mutableState.value.paging,
                        page.items,
                        hasMore,
                        offset + page.items.size,
                    )
              }
          log.i {
            "DM搜索 -> 成功(queryLength=${query.length},offset=$offset,count=${page.items.size})"
          }
          updatePaging(next)
        }
        .onFailure { error ->
          if (requestGeneration != generation || mutableState.value.query != query) return@onFailure
          updatePaging(
              when {
                replace && rollback != null -> reducer.reduceJumpError(rollback, error)
                replace -> reducer.reduceFirstPageError(mutableState.value.paging, error)
                prepend -> reducer.reducePrependError(mutableState.value.paging, error)
                else -> reducer.reduceAppendError(mutableState.value.paging, error)
              }
          )
        }
  }

  private fun updatePaging(paging: PaginationSnapshot<DM>) {
    mutableState.value = mutableState.value.copy(paging = paging)
  }
}
