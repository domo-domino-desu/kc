package ddd.kc.ui.pages.more

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.data.repository.PostRepository
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class FavoritesState(
    val creators: List<Creator> = emptyList(),
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

class FavoritesScreenModel(
    private val creatorRepo: CreatorRepository,
    private val postRepo: PostRepository,
) : StateScreenModel<FavoritesState>(FavoritesState()) {
  private val log = KcLog.withTag("FavoritesScreenModel")

  fun load(platform: Platform, forceRefresh: Boolean = false) {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            isLoading = current.creators.isEmpty() && current.posts.isEmpty(),
            isRefreshing =
                forceRefresh && (current.creators.isNotEmpty() || current.posts.isNotEmpty()),
            errorMessage = null,
        )
    screenModelScope.launch {
      log.i { "收藏列表 -> 加载开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
      val creatorsDeferred = async {
        runCatching { creatorRepo.getFavoriteCreators(platform, forceRefresh) }
      }
      val postsDeferred = async {
        runCatching { postRepo.getFavoritePosts(platform, forceRefresh) }
      }
      val creators = creatorsDeferred.await()
      val posts = postsDeferred.await()
      log.i {
        "收藏列表 -> 加载完成(platform=${platform.name},creators=${creators.getOrNull()?.size ?: 0},posts=${posts.getOrNull()?.size ?: 0})"
      }
      val latest = mutableState.value
      mutableState.value =
          FavoritesState(
              creators = creators.getOrDefault(latest.creators),
              posts = posts.getOrDefault(latest.posts),
              errorMessage = (creators.exceptionOrNull() ?: posts.exceptionOrNull())?.message,
          )
    }
  }
}
