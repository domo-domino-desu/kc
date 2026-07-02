package ddd.kc.ui.pages.dm

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.preserveRefreshUi
import ddd.kc.data.network.PageInfo
import ddd.kc.data.network.PagedResult
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.ui.state.pageInfoForOffset
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("DmSearchScreenModel")
private const val PAGE_SIZE = 50
private const val AUTO_PREPEND_ARM_INDEX = 3

data class DmSearchState(
    val query: String = "",
    val result: QueryState<PagedResult<DM>> = QueryState(),
    val dms: List<DM> = emptyList(),
    val startOffset: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val pageInfo: PageInfo? = null,
    val visibleOffset: Int = startOffset,
    val autoPrependArmed: Boolean = startOffset <= 0,
    val isLoadingPrevious: Boolean = false,
    val isLoadingMore: Boolean = false,
    val prependErrorMessage: String? = null,
    val appendErrorMessage: String? = null,
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message

  val visiblePageInfo: PageInfo?
    get() = pageInfoForOffset(pageInfo, visibleOffset, PAGE_SIZE)

  val canAutoLoadPrevious: Boolean
    get() = startOffset > 0 && autoPrependArmed
}

class DmSearchScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<DmSearchState>(DmSearchState()) {
  private var searchJob: Job? = null
  private var platform = Platform.PAWCHIVE

  fun init(platform: Platform) {
    val platformChanged = this.platform != platform
    this.platform = platform
    if (platformChanged) {
      mutableState.value = DmSearchState()
    }
  }

  fun onQueryChanged(query: String) {
    mutableState.value =
        mutableState.value.copy(
            query = query,
            dms = emptyList(),
            startOffset = 0,
            offset = 0,
            hasMore = true,
            pageInfo = null,
            visibleOffset = 0,
            autoPrependArmed = true,
            isLoadingPrevious = false,
            isLoadingMore = false,
            prependErrorMessage = null,
            appendErrorMessage = null,
        )
    searchJob?.cancel()
    if (query.isBlank()) {
      mutableState.value = mutableState.value.copy(dms = emptyList(), result = QueryState())
      return
    }
    searchJob =
        screenModelScope.launch {
          try {
            delay(300)
            search(query)
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }

  fun refresh() {
    searchJob?.cancel()
    val query = mutableState.value.query
    if (query.isBlank()) return
    searchJob = screenModelScope.launch { search(query) }
  }

  private suspend fun search(query: String) {
    mutableState.value =
        mutableState.value.copy(
            result =
                mutableState.value.result.copy(
                    isLoading = mutableState.value.dms.isEmpty(),
                    isRefreshing = mutableState.value.dms.isNotEmpty(),
                    error = null,
                )
        )
    creatorRepo.observeDmsPage(query = query, offset = 0, forceRefresh = true).collect { next ->
      val dms = next.data?.items ?: mutableState.value.dms
      val result = next.preserveRefreshUi(dms.isNotEmpty())
      log.i { "DM搜索 -> 状态(queryLength=${query.length},count=${dms.size})" }
      mutableState.value =
          mutableState.value.copy(
              result = result,
              dms = dms,
              startOffset = 0,
              offset = dms.size,
              hasMore = next.data?.pageInfo?.hasNext ?: (dms.size >= PAGE_SIZE),
              pageInfo = next.data?.pageInfo,
              visibleOffset = 0,
              autoPrependArmed = true,
              isLoadingPrevious = false,
              prependErrorMessage = null,
              appendErrorMessage = null,
          )
    }
  }

  fun loadMore() {
    val state = mutableState.value
    if (state.query.isBlank() || state.isLoading || state.isLoadingMore || !state.hasMore) return
    mutableState.value = state.copy(isLoadingMore = true, appendErrorMessage = null)
    screenModelScope.launch { fetchPage(state.offset, replace = false) }
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (
        state.query.isBlank() ||
            !state.canAutoLoadPrevious ||
            state.isLoading ||
            state.result.isRefreshing ||
            state.isLoadingPrevious ||
            state.isLoadingMore
    )
        return
    mutableState.value = state.copy(isLoadingPrevious = true, prependErrorMessage = null)
    screenModelScope.launch {
      fetchPage((state.startOffset - PAGE_SIZE).coerceAtLeast(0), replace = false, prepend = true)
    }
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    if (state.query.isBlank()) return
    val targetPage = page.coerceIn(1, state.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    mutableState.value =
        state.copy(
            result =
                state.result.copy(
                    isLoading = state.dms.isEmpty(),
                    isRefreshing = state.dms.isNotEmpty(),
                    error = null,
                ),
            startOffset = offset,
            visibleOffset = offset,
            autoPrependArmed = offset <= 0,
            isLoadingMore = false,
            isLoadingPrevious = false,
            appendErrorMessage = null,
            prependErrorMessage = null,
        )
    screenModelScope.launch { fetchPage(offset, replace = true, rollbackState = state) }
  }

  fun onVisibleItemIndex(firstVisibleItemIndex: Int) {
    val state = mutableState.value
    if (state.dms.isEmpty()) return
    val relativeIndex = firstVisibleItemIndex.coerceAtLeast(0).coerceAtMost(state.dms.lastIndex)
    val absoluteOffset = state.startOffset + relativeIndex
    val autoPrependArmed = state.autoPrependArmed || relativeIndex > AUTO_PREPEND_ARM_INDEX
    if (state.visibleOffset == absoluteOffset && state.autoPrependArmed == autoPrependArmed) return
    mutableState.value =
        state.copy(visibleOffset = absoluteOffset, autoPrependArmed = autoPrependArmed)
  }

  private suspend fun fetchPage(
      offset: Int,
      replace: Boolean,
      prepend: Boolean = false,
      rollbackState: DmSearchState? = null,
  ) {
    val query = mutableState.value.query
    runCatching { creatorRepo.searchDMsPage(platform, query, offset, forceRefresh = true) }
        .onSuccess { page ->
          val dms =
              when {
                replace -> page.items
                prepend ->
                    (page.items + mutableState.value.dms).distinctBy {
                      it.hash ?: it.content.orEmpty()
                    }
                else ->
                    (mutableState.value.dms + page.items).distinctBy {
                      it.hash ?: it.content.orEmpty()
                    }
              }
          mutableState.value =
              mutableState.value.copy(
                  result = QueryState(data = page),
                  dms = dms,
                  startOffset = if (replace || prepend) offset else mutableState.value.startOffset,
                  offset = if (prepend) mutableState.value.offset else offset + page.items.size,
                  hasMore = page.pageInfo?.hasNext ?: (page.items.size >= PAGE_SIZE),
                  pageInfo = if (replace) page.pageInfo else mutableState.value.pageInfo,
                  visibleOffset =
                      when {
                        replace -> offset
                        prepend -> mutableState.value.visibleOffset
                        else -> mutableState.value.visibleOffset
                      },
                  autoPrependArmed =
                      when {
                        replace -> offset <= 0
                        prepend -> true
                        else -> mutableState.value.autoPrependArmed
                      },
                  isLoadingPrevious = false,
                  isLoadingMore = false,
                  prependErrorMessage = null,
                  appendErrorMessage = null,
              )
        }
        .onFailure { error ->
          mutableState.value =
              if (replace && rollbackState != null) {
                rollbackState.copy(
                    result =
                        rollbackState.result.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.toQueryError(),
                        ),
                    isLoadingPrevious = false,
                    isLoadingMore = false,
                    prependErrorMessage = null,
                    appendErrorMessage = null,
                )
              } else {
                mutableState.value.copy(
                    result =
                        mutableState.value.result.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.toQueryError(),
                        ),
                    isLoadingPrevious = false,
                    isLoadingMore = false,
                    prependErrorMessage = if (prepend) error.message else null,
                    appendErrorMessage = if (!prepend && offset != 0) error.message else null,
                )
              }
        }
  }
}
