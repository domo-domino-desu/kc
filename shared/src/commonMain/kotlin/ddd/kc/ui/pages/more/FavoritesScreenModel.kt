package ddd.kc.ui.pages.more

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.data.repository.PostRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class FavoritesState(
    val creators: List<Creator> = emptyList(),
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class FavoritesScreenModel(
    private val creatorRepo: CreatorRepository,
    private val postRepo: PostRepository,
) : StateScreenModel<FavoritesState>(FavoritesState()) {
  private val log = KcLog.withTag("FavoritesScreenModel")

  fun load(platform: Platform) {
    mutableState.value = FavoritesState(isLoading = true)
    screenModelScope.launch {
      log.i { "收藏列表 -> 加载开始(platform=${platform.name})" }
      val creatorsDeferred = async { runCatching { creatorRepo.getFavoriteCreators(platform) } }
      val postsDeferred = async { runCatching { postRepo.getFavoritePosts(platform) } }
      val creators = creatorsDeferred.await()
      val posts = postsDeferred.await()
      log.i {
        "收藏列表 -> 加载完成(platform=${platform.name},creators=${creators.getOrNull()?.size ?: 0},posts=${posts.getOrNull()?.size ?: 0})"
      }
      mutableState.value =
          FavoritesState(
              creators = creators.getOrDefault(emptyList()),
              posts = posts.getOrDefault(emptyList()),
              errorMessage = (creators.exceptionOrNull() ?: posts.exceptionOrNull())?.message,
          )
    }
  }
}
