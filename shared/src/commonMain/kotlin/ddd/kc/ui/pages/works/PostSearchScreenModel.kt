package ddd.kc.ui.pages.works

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.PagedResult
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.key
import ddd.kc.data.remote.network.asException
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.data.remote.repository.awaitData
import ddd.kc.ui.components.state.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.state.PaginationReducer
import ddd.kc.ui.components.state.PaginationSnapshot
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("PostSearchScreenModel")
private const val PAGE_SIZE = DEFAULT_PAGE_SIZE

data class PostSearchState(
    val query: String = "",
    val defaultPopularDate: String? = null,
    val paging: PaginationSnapshot<Post> = PaginationSnapshot(),
) {
  val posts
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

  val startOffset
    get() = paging.startOffset
}

class PostSearchScreenModel(
    private val postRepo: PostRepository,
) : StateScreenModel<PostSearchState>(PostSearchState()) {
  private val reducer = PaginationReducer<Post, PostKey> { it.key }
  private var searchJob: Job? = null
  private var generation = 0L

  fun init() {
    if (mutableState.value.posts.isEmpty()) loadDefault()
  }

  fun refresh(forceRefreshDefault: Boolean = true) {
    searchJob?.cancel()
    val requestGeneration = ++generation
    val query = mutableState.value.query
    searchJob =
        screenModelScope.launch {
          if (query.isBlank()) loadDefaultPage(forceRefreshDefault, requestGeneration)
          else loadSearchPage(query, requestGeneration)
        }
  }

  fun onQueryChanged(query: String) {
    searchJob?.cancel()
    val requestGeneration = ++generation
    mutableState.value = PostSearchState(query = query)
    searchJob =
        screenModelScope.launch {
          try {
            if (query.isNotBlank()) delay(300)
            if (query.isBlank()) loadDefaultPage(false, requestGeneration)
            else loadSearchPage(query, requestGeneration)
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }

  fun loadMore() {
    val state = mutableState.value
    if (!reducer.canLoadMore(state.paging)) return
    screenModelScope.launch {
      updatePaging(reducer.beginAppend(mutableState.value.paging))
      fetchPage(mutableState.value.paging.offset)
    }
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (!reducer.canLoadPrevious(state.paging)) return
    val offset = (state.paging.startOffset - PAGE_SIZE).coerceAtLeast(0)
    screenModelScope.launch {
      updatePaging(reducer.beginPrepend(mutableState.value.paging))
      fetchPage(offset, prepend = true)
    }
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    val targetPage = page.coerceIn(1, state.paging.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    searchJob?.cancel()
    val requestGeneration = ++generation
    val previous = state.paging
    updatePaging(reducer.beginJump(previous, offset))
    screenModelScope.launch {
      fetchPage(offset, replace = true, rollback = previous, requestGeneration = requestGeneration)
    }
  }

  fun onVisiblePostIndex(firstVisiblePostIndex: Int) {
    updatePaging(
        reducer.updateVisiblePage(mutableState.value.paging, firstVisiblePostIndex, PAGE_SIZE)
    )
  }

  private fun loadDefault(forceRefresh: Boolean = false) {
    val requestGeneration = generation
    searchJob = screenModelScope.launch { loadDefaultPage(forceRefresh, requestGeneration) }
  }

  private suspend fun loadDefaultPage(forceRefresh: Boolean, requestGeneration: Long) {
    updatePaging(reducer.beginLoad(mutableState.value.paging, forceRefresh))
    postRepo.observePopularPostsPage(null, "day", 0, forceRefresh).collect { next ->
      if (requestGeneration != generation || mutableState.value.query.isNotBlank()) return@collect
      next.data?.let { page ->
        mutableState.value =
            mutableState.value.copy(
                defaultPopularDate =
                    page.info.minDate ?: page.info.date ?: mutableState.value.defaultPopularDate,
                paging =
                    reducer.reduceFirstPage(
                        mutableState.value.paging,
                        page.posts,
                        page.pageInfo?.hasNext ?: (page.posts.size >= PAGE_SIZE),
                        page.posts.size,
                        page.pageInfo,
                    ),
            )
      }
      next.error?.let {
        updatePaging(reducer.reduceFirstPageError(mutableState.value.paging, it.asException()))
      }
    }
  }

  private suspend fun loadSearchPage(query: String, requestGeneration: Long) {
    updatePaging(reducer.beginLoad(mutableState.value.paging, forceRefresh = true))
    resultOfSuspend { postRepo.searchPostsPage(query, 0, null, null, true) }
        .onSuccess { page ->
          if (requestGeneration != generation || mutableState.value.query != query) return@onSuccess
          updatePaging(
              reducer.reduceFirstPage(
                  mutableState.value.paging,
                  page.items,
                  page.pageInfo?.hasNext ?: (page.items.size >= PAGE_SIZE),
                  page.items.size,
                  page.pageInfo,
              )
          )
        }
        .onFailure {
          if (requestGeneration == generation) {
            updatePaging(reducer.reduceFirstPageError(mutableState.value.paging, it))
          }
        }
  }

  private suspend fun fetchPage(
      offset: Int,
      replace: Boolean = false,
      prepend: Boolean = false,
      rollback: PaginationSnapshot<Post>? = null,
      requestGeneration: Long = generation,
  ) {
    val requestQuery = mutableState.value.query
    resultOfSuspend {
          if (requestQuery.isBlank()) {
            val page =
                postRepo
                    .observePopularPostsPage(
                        mutableState.value.defaultPopularDate,
                        "day",
                        offset,
                    )
                    .awaitData()
            PagedResult(page.posts, page.pageInfo)
          } else {
            postRepo.searchPostsPage(requestQuery, offset, null, null, true)
          }
        }
        .onSuccess { page ->
          if (requestGeneration != generation || mutableState.value.query != requestQuery)
              return@onSuccess
          val hasMore = page.pageInfo?.hasNext ?: (page.items.size >= PAGE_SIZE)
          updatePaging(
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
          )
          log.i {
            "作品搜索 -> 成功(queryLength=${requestQuery.length},offset=$offset,count=${page.items.size})"
          }
        }
        .onFailure { error ->
          if (requestGeneration != generation || mutableState.value.query != requestQuery)
              return@onFailure
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

  private fun updatePaging(paging: PaginationSnapshot<Post>) {
    mutableState.value = mutableState.value.copy(paging = paging)
  }
}
