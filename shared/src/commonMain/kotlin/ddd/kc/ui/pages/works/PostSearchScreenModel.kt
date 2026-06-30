package ddd.kc.ui.pages.works

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryState
import ddd.kc.data.repository.PostRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("PostSearchScreenModel")

data class PostSearchState(
    val query: String = "",
    val result: QueryState<List<Post>> = QueryState(isLoading = true),
    val posts: List<Post> = emptyList(),
    val isLoadingMore: Boolean = false,
    val appendErrorMessage: String? = null,
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message
}

class PostSearchScreenModel(
    private val postRepo: PostRepository,
) : StateScreenModel<PostSearchState>(PostSearchState()) {
  private var searchJob: Job? = null
  private var platform = Platform.PAWCHIVE

  fun init(platform: Platform) {
    val platformChanged = this.platform != platform
    this.platform = platform
    if (platformChanged) {
      mutableState.value = PostSearchState(query = mutableState.value.query)
      loadDefault()
    } else if (mutableState.value.posts.isEmpty()) {
      loadDefault()
    }
  }

  fun refresh(forceRefreshDefault: Boolean = true) {
    searchJob?.cancel()
    val query = mutableState.value.query
    if (query.isBlank()) {
      loadDefault(forceRefresh = forceRefreshDefault)
    } else {
      search(query)
    }
  }

  fun onQueryChanged(query: String) {
    mutableState.value = mutableState.value.copy(query = query)
    searchJob?.cancel()
    if (query.isBlank()) {
      loadDefault()
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

  private fun loadDefault(forceRefresh: Boolean = false) {
    mutableState.value =
        mutableState.value.copy(
            result =
                mutableState.value.result.copy(
                    isLoading = mutableState.value.posts.isEmpty(),
                    isRefreshing = mutableState.value.posts.isNotEmpty(),
                    error = null,
                )
        )
    screenModelScope.launch {
      postRepo
          .observePopularPostsPage(
              date = null,
              period = "day",
              offset = 0,
              forceRefresh = forceRefresh,
          )
          .collect { next ->
            val posts = next.data?.posts ?: mutableState.value.posts
            log.i { "作品搜索默认Popular -> 状态(count=${posts.size})" }
            mutableState.value =
                mutableState.value.copy(
                    result =
                        QueryState(
                            data = posts,
                            isLoading = next.isLoading,
                            isRefreshing = next.isRefreshing,
                            isFromCache = next.isFromCache,
                            isStale = next.isStale,
                            error = next.error,
                            lastUpdatedAtMillis = next.lastUpdatedAtMillis,
                        ),
                    posts = posts,
                )
          }
    }
  }

  private fun search(query: String) {
    mutableState.value =
        mutableState.value.copy(
            result =
                mutableState.value.result.copy(
                    isLoading = mutableState.value.posts.isEmpty(),
                    isRefreshing = mutableState.value.posts.isNotEmpty(),
                    error = null,
                )
        )
    screenModelScope.launch {
      postRepo
          .observePostSearch(query, offset = 0, tag = null, service = null, forceRefresh = true)
          .collect { next ->
            val posts = next.data ?: mutableState.value.posts
            log.i { "作品搜索 -> 状态(queryLength=${query.length},count=${posts.size})" }
            mutableState.value = mutableState.value.copy(result = next, posts = posts)
          }
    }
  }
}
