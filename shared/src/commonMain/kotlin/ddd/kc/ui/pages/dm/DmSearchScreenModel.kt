package ddd.kc.ui.pages.dm

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = KcLog.withTag("DmSearchScreenModel")

data class DmSearchState(
    val query: String = "",
    val dms: List<DM> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class DmSearchScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<DmSearchState>(DmSearchState()) {
  private var searchJob: Job? = null
  private var platform = Platform.KEMONO

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
      mutableState.value =
          mutableState.value.copy(dms = emptyList(), isLoading = false, errorMessage = null)
      return
    }
    searchJob =
        screenModelScope.launch {
          try {
            delay(300)
            mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
            runCatching { creatorRepo.searchDMs(platform, query, 0) }
                .onSuccess {
                  log.i { "DM搜索 -> 成功(queryLength=${query.length},count=${it.size})" }
                  mutableState.value = mutableState.value.copy(dms = it, isLoading = false)
                }
                .onFailure {
                  if (it is CancellationException) return@launch
                  log.e(it) { "DM搜索 -> 失败" }
                  mutableState.value =
                      mutableState.value.copy(isLoading = false, errorMessage = it.message)
                }
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
    mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
    runCatching { creatorRepo.searchDMs(platform, query, 0) }
        .onSuccess {
          log.i { "DM搜索 -> 成功(queryLength=${query.length},count=${it.size})" }
          mutableState.value = mutableState.value.copy(dms = it, isLoading = false)
        }
        .onFailure {
          if (it is CancellationException) return
          log.e(it) { "DM搜索 -> 失败" }
          mutableState.value = mutableState.value.copy(isLoading = false, errorMessage = it.message)
        }
  }
}
