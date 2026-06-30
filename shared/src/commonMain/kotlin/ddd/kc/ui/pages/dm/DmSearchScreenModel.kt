package ddd.kc.ui.pages.dm

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.data.model.QueryState
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("DmSearchScreenModel")

data class DmSearchState(
    val query: String = "",
    val result: QueryState<List<DM>> = QueryState(),
    val dms: List<DM> = emptyList(),
    val isLoadingMore: Boolean = false,
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message
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
    mutableState.value = mutableState.value.copy(query = query)
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
    creatorRepo.observeDms(query = query, offset = 0, forceRefresh = true).collect { next ->
      val dms = next.data ?: mutableState.value.dms
      log.i { "DM搜索 -> 状态(queryLength=${query.length},count=${dms.size})" }
      mutableState.value = mutableState.value.copy(result = next, dms = dms)
    }
  }
}
