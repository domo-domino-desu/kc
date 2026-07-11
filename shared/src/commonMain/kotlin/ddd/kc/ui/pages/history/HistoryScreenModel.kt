package ddd.kc.ui.pages.history

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryError
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.ActivityHistoryRepository
import ddd.kc.utils.coroutines.resultOfSuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class HistoryUiState(
    val creators: List<Creator> = emptyList(),
    val posts: List<Post> = emptyList(),
    val loading: Boolean = true,
    val error: QueryError? = null,
)

class HistoryScreenModel(private val repository: ActivityHistoryRepository) :
    StateScreenModel<HistoryUiState>(HistoryUiState()) {
  fun load() {
    if (!mutableState.value.loading && mutableState.value.error == null) return
    screenModelScope.launch {
      mutableState.value = mutableState.value.copy(loading = true, error = null)
      val creators = async { resultOfSuspend { repository.loadCreatorHistory() } }
      val posts = async { resultOfSuspend { repository.loadPostHistory() } }
      val creatorResult = creators.await()
      val postResult = posts.await()
      val current = mutableState.value
      mutableState.value =
          current.copy(
              creators = creatorResult.getOrDefault(current.creators),
              posts = postResult.getOrDefault(current.posts),
              loading = false,
              error =
                  (creatorResult.exceptionOrNull() ?: postResult.exceptionOrNull())?.toQueryError(),
          )
    }
  }
}
