package ddd.kc.ui.pages.works

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.PagedResult
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryError
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.key
import ddd.kc.data.model.preserveRefreshUi
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.PostRepository
import ddd.kc.data.repository.awaitData
import ddd.kc.ui.state.pageInfoForOffset
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("PostSearchScreenModel")
private const val PAGE_SIZE = 50

data class PostSearchState(
    val query: String = "",
    val result: QueryState<PagedResult<Post>> = QueryState(isLoading = true),
    val posts: List<Post> = emptyList(),
    val defaultPopularDate: String? = null,
    val startOffset: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val pageInfo: PageInfo? = null,
    val visibleOffset: Int = startOffset,
    val autoPrependArmed: Boolean = startOffset <= 0,
    val isLoadingPrevious: Boolean = false,
    val isLoadingMore: Boolean = false,
    val prependError: QueryError? = null,
    val appendError: QueryError? = null,
) {
  val isLoading: Boolean
    get() = result.isLoading

  val error: ddd.kc.data.model.QueryError?
    get() = result.error

  val visiblePageInfo: PageInfo?
    get() = pageInfoForOffset(pageInfo, visibleOffset, PAGE_SIZE)

  val canAutoLoadPrevious: Boolean
    get() = startOffset > 0
}

class PostSearchScreenModel(
    private val postRepo: PostRepository,
) : StateScreenModel<PostSearchState>(PostSearchState()) {
  private var searchJob: Job? = null
  private var generation: Long = 0

  fun init() {
    if (mutableState.value.posts.isEmpty()) loadDefault()
  }

  fun refresh(forceRefreshDefault: Boolean = true) {
    searchJob?.cancel()
    generation++
    val query = mutableState.value.query
    if (query.isBlank()) {
      loadDefault(forceRefresh = forceRefreshDefault)
    } else {
      val requestGeneration = generation
      searchJob = screenModelScope.launch { search(query, requestGeneration) }
    }
  }

  fun onQueryChanged(query: String) {
    mutableState.value =
        mutableState.value.copy(
            query = query,
            posts = emptyList(),
            defaultPopularDate = null,
            startOffset = 0,
            offset = 0,
            hasMore = true,
            pageInfo = null,
            visibleOffset = 0,
            autoPrependArmed = true,
            isLoadingPrevious = false,
            isLoadingMore = false,
            prependError = null,
            appendError = null,
        )
    searchJob?.cancel()
    generation++
    if (query.isBlank()) {
      loadDefault()
      return
    }
    val requestGeneration = generation
    searchJob =
        screenModelScope.launch {
          try {
            delay(300)
            search(query, requestGeneration)
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }

  private fun loadDefault(forceRefresh: Boolean = false) {
    val requestGeneration = generation
    mutableState.value =
        mutableState.value.copy(
            result =
                mutableState.value.result.copy(
                    isLoading = mutableState.value.posts.isEmpty(),
                    isRefreshing = mutableState.value.posts.isNotEmpty(),
                    error = null,
                )
        )
    searchJob =
        screenModelScope.launch {
          postRepo
              .observePopularPostsPage(
                  date = null,
                  period = "day",
                  offset = 0,
                  forceRefresh = forceRefresh,
              )
              .collect { next ->
                if (requestGeneration != generation || mutableState.value.query.isNotBlank())
                    return@collect
                val posts = next.data?.posts ?: mutableState.value.posts
                val result =
                    QueryState(
                            data = next.data?.let { PagedResult(it.posts, it.pageInfo) },
                            isLoading = next.isLoading,
                            isRefreshing = next.isRefreshing,
                            isFromCache = next.isFromCache,
                            isStale = next.isStale,
                            error = next.error,
                            lastUpdatedAtMillis = next.lastUpdatedAtMillis,
                        )
                        .preserveRefreshUi(posts.isNotEmpty())
                log.i { "作品搜索默认Popular -> 状态(count=${posts.size})" }
                mutableState.value =
                    mutableState.value.copy(
                        result = result,
                        posts = posts,
                        defaultPopularDate =
                            next.data?.info?.minDate
                                ?: next.data?.info?.date
                                ?: mutableState.value.defaultPopularDate,
                        startOffset = 0,
                        offset = posts.size,
                        hasMore = next.data?.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                        pageInfo = next.data?.pageInfo,
                        visibleOffset = 0,
                        autoPrependArmed = true,
                        isLoadingPrevious = false,
                        prependError = null,
                    )
              }
        }
  }

  private suspend fun search(query: String, requestGeneration: Long) {
    mutableState.value =
        mutableState.value.copy(
            result =
                mutableState.value.result.copy(
                    isLoading = mutableState.value.posts.isEmpty(),
                    isRefreshing = mutableState.value.posts.isNotEmpty(),
                    error = null,
                )
        )
    postRepo
        .observePostSearchPage(query, offset = 0, tag = null, service = null, forceRefresh = true)
        .collect { next ->
          if (requestGeneration != generation || mutableState.value.query != query) return@collect
          val posts = next.data?.items ?: mutableState.value.posts
          val result = next.preserveRefreshUi(posts.isNotEmpty())
          log.i { "作品搜索 -> 状态(queryLength=${query.length},count=${posts.size})" }
          mutableState.value =
              mutableState.value.copy(
                  result = result,
                  posts = posts,
                  startOffset = 0,
                  offset = posts.size,
                  hasMore = next.data?.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                  pageInfo = next.data?.pageInfo,
                  visibleOffset = 0,
                  autoPrependArmed = true,
                  isLoadingPrevious = false,
                  prependError = null,
                  appendError = null,
              )
        }
  }

  fun loadMore() {
    val state = mutableState.value
    if (state.isLoading || state.isLoadingMore || !state.hasMore) return
    mutableState.value = state.copy(isLoadingMore = true, appendError = null)
    screenModelScope.launch { fetchPage(offset = state.offset, replace = false) }
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (
        !state.canAutoLoadPrevious ||
            state.isLoading ||
            state.result.isRefreshing ||
            state.isLoadingPrevious ||
            state.isLoadingMore
    )
        return
    mutableState.value = state.copy(isLoadingPrevious = true, prependError = null)
    screenModelScope.launch {
      fetchPage(
          offset = (state.startOffset - PAGE_SIZE).coerceAtLeast(0),
          replace = false,
          prepend = true,
      )
    }
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    val targetPage = page.coerceIn(1, state.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    searchJob?.cancel()
    generation++
    mutableState.value =
        state.copy(
            result =
                state.result.copy(
                    isLoading = state.posts.isEmpty(),
                    isRefreshing = state.posts.isNotEmpty(),
                    error = null,
                ),
            startOffset = offset,
            visibleOffset = offset,
            autoPrependArmed = offset <= 0,
            isLoadingMore = false,
            isLoadingPrevious = false,
            appendError = null,
            prependError = null,
        )
    val requestGeneration = generation
    screenModelScope.launch {
      fetchPage(
          offset = offset,
          replace = true,
          rollbackState = state,
          requestGeneration = requestGeneration,
      )
    }
  }

  fun onVisiblePostIndex(firstVisiblePostIndex: Int) {
    val state = mutableState.value
    if (state.posts.isEmpty()) return
    val relativeIndex = firstVisiblePostIndex.coerceAtLeast(0).coerceAtMost(state.posts.lastIndex)
    val absoluteOffset = state.startOffset + relativeIndex
    if (state.visibleOffset == absoluteOffset) return
    mutableState.value = state.copy(visibleOffset = absoluteOffset)
  }

  private suspend fun fetchPage(
      offset: Int,
      replace: Boolean,
      prepend: Boolean = false,
      rollbackState: PostSearchState? = null,
      requestGeneration: Long = generation,
  ) {
    val requestQuery = mutableState.value.query
    resultOfSuspend {
          if (requestQuery.isBlank()) {
            val page =
                postRepo
                    .observePopularPostsPage(
                        date = mutableState.value.defaultPopularDate,
                        period = "day",
                        offset = offset,
                    )
                    .awaitData()
            PagedResult(page.posts, page.pageInfo)
          } else {
            postRepo.searchPostsPage(
                query = requestQuery,
                offset = offset,
                tag = null,
                service = null,
                forceRefresh = true,
            )
          }
        }
        .onSuccess { page ->
          if (requestGeneration != generation || mutableState.value.query != requestQuery) {
            return@onSuccess
          }
          val posts =
              when {
                replace -> page.items
                prepend -> (page.items + mutableState.value.posts).distinctBy { it.key }
                else -> (mutableState.value.posts + page.items).distinctBy { it.key }
              }
          mutableState.value =
              mutableState.value.copy(
                  result = QueryState(data = page),
                  posts = posts,
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
                  prependError = null,
                  appendError = null,
              )
        }
        .onFailure { error ->
          if (requestGeneration != generation || mutableState.value.query != requestQuery) {
            return@onFailure
          }
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
                    prependError = null,
                    appendError = null,
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
                    prependError = if (prepend) error.toQueryError() else null,
                    appendError = if (!prepend && offset != 0) error.toQueryError() else null,
                )
              }
        }
  }
}
