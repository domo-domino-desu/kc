package ddd.kc.ui.pages.works

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.repository.PostRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("PostSearchScreenModel")

data class PostSearchState(
    val query: String = "",
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class PostSearchScreenModel(
    private val postRepo: PostRepository,
) : StateScreenModel<PostSearchState>(PostSearchState()) {
  private var searchJob: Job? = null
  private var platform = Platform.KEMONO

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
    mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
    screenModelScope.launch {
      runCatching { postRepo.getPopularPosts(platform, forceRefresh) }
          .onSuccess {
            log.i { "作品搜索默认Popular -> 成功(count=${it.size})" }
            mutableState.value = mutableState.value.copy(posts = it, isLoading = false)
          }
          .onFailure {
            log.e(it) { "作品搜索默认Popular -> 失败" }
            mutableState.value =
                mutableState.value.copy(isLoading = false, errorMessage = it.message)
          }
    }
  }

  private fun search(query: String) {
    mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
    screenModelScope.launch {
      runCatching { postRepo.searchPosts(platform, query, 0, null, null) }
          .onSuccess {
            log.i { "作品搜索 -> 成功(queryLength=${query.length},count=${it.size})" }
            mutableState.value = mutableState.value.copy(posts = it, isLoading = false)
          }
          .onFailure {
            if (it is CancellationException) return@launch
            log.e(it) { "作品搜索 -> 失败" }
            mutableState.value =
                mutableState.value.copy(isLoading = false, errorMessage = it.message)
          }
    }
  }
}
