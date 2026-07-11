package ddd.kc.ui.pages.more

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryError
import ddd.kc.data.remote.network.toQueryError
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class FavoritesState(
    val creators: List<Creator> = emptyList(),
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: QueryError? = null,
)

class FavoritesScreenModel(
    private val creatorRepo: CreatorRepository,
    private val postRepo: PostRepository,
) : StateScreenModel<FavoritesState>(FavoritesState()) {
  private val log = KcLog.withTag("FavoritesScreenModel")
  private var loadJob: Job? = null
  private var generation: Long = 0

  fun load(forceRefresh: Boolean = false) {
    loadJob?.cancel()
    generation++
    val requestGeneration = generation
    val current = mutableState.value
    mutableState.value =
        current.copy(
            isLoading = current.creators.isEmpty() && current.posts.isEmpty(),
            isRefreshing =
                forceRefresh && (current.creators.isNotEmpty() || current.posts.isNotEmpty()),
            error = null,
        )
    loadJob =
        screenModelScope.launch {
          log.i { "收藏列表 -> 加载开始(forceRefresh=$forceRefresh)" }
          val creatorsDeferred = async {
            resultOfSuspend { creatorRepo.getFavoriteCreators(forceRefresh) }
          }
          val postsDeferred = async { resultOfSuspend { postRepo.getFavoritePosts(forceRefresh) } }
          val creators = creatorsDeferred.await()
          val posts = postsDeferred.await()
          if (requestGeneration != generation) return@launch
          log.i {
            "收藏列表 -> 加载完成(creators=${creators.getOrNull()?.size ?: 0},posts=${posts.getOrNull()?.size ?: 0})"
          }
          val latest = mutableState.value
          mutableState.value =
              FavoritesState(
                  creators = creators.getOrDefault(latest.creators),
                  posts = posts.getOrDefault(latest.posts),
                  error = (creators.exceptionOrNull() ?: posts.exceptionOrNull())?.toQueryError(),
              )
        }
  }
}
