package ddd.kc.ui.pages.creators

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.util.logging.KcLog
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.order_asc
import kc.shared.generated.resources.order_desc
import kc.shared.generated.resources.sort_alphabetical
import kc.shared.generated.resources.sort_date_indexed
import kc.shared.generated.resources.sort_date_updated
import kc.shared.generated.resources.sort_popularity
import kc.shared.generated.resources.sort_service_name
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

enum class CreatorSort(val apiValue: String) {
  FAVORITED("favorited"),
  INDEXED("indexed"),
  UPDATED("updated"),
  NAME("name"),
  SERVICE("service"),
}

enum class SortOrder(val apiValue: String) {
  DESC("desc"),
  ASC("asc"),
}

@Composable
fun creatorSortLabel(sort: CreatorSort): String =
    when (sort) {
      CreatorSort.FAVORITED -> stringResource(Res.string.sort_popularity)
      CreatorSort.INDEXED -> stringResource(Res.string.sort_date_indexed)
      CreatorSort.UPDATED -> stringResource(Res.string.sort_date_updated)
      CreatorSort.NAME -> stringResource(Res.string.sort_alphabetical)
      CreatorSort.SERVICE -> stringResource(Res.string.sort_service_name)
    }

@Composable
fun sortOrderLabel(order: SortOrder): String =
    when (order) {
      SortOrder.DESC -> stringResource(Res.string.order_desc)
      SortOrder.ASC -> stringResource(Res.string.order_asc)
    }

private val log = KcLog.withTag("CreatorSearchScreenModel")

data class CreatorSearchState(
    val query: String = "",
    val selectedService: String? = null,
    val sortBy: CreatorSort = CreatorSort.FAVORITED,
    val sortOrder: SortOrder = SortOrder.DESC,
    val creators: List<Creator> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class CreatorSearchScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<CreatorSearchState>(CreatorSearchState()) {
  private var searchJob: Job? = null
  private var platform = Platform.KEMONO

  fun init(platform: Platform) {
    val platformChanged = this.platform != platform
    this.platform = platform
    if (platformChanged) {
      mutableState.value = CreatorSearchState(query = mutableState.value.query)
    }
    if (platformChanged || mutableState.value.creators.isEmpty()) reload(delayMs = 0)
  }

  fun onQueryChanged(query: String) {
    mutableState.value = mutableState.value.copy(query = query)
    reload()
  }

  fun onServiceChanged(service: String?) {
    mutableState.value = mutableState.value.copy(selectedService = service)
    reload(delayMs = 100)
  }

  fun onSortChanged(sort: CreatorSort) {
    mutableState.value = mutableState.value.copy(sortBy = sort)
    reload(delayMs = 100)
  }

  fun onSortOrderChanged(order: SortOrder) {
    mutableState.value = mutableState.value.copy(sortOrder = order)
    reload(delayMs = 100)
  }

  fun refresh() {
    reload(delayMs = 0, forceRefresh = true)
  }

  private fun reload(delayMs: Long = 300, forceRefresh: Boolean = false) {
    searchJob?.cancel()
    searchJob =
        screenModelScope.launch {
          try {
            if (delayMs > 0) delay(delayMs)
            val state = mutableState.value
            mutableState.value = state.copy(isLoading = true, errorMessage = null)
            runCatching {
                  creatorRepo.searchCreators(
                      platform,
                      state.query,
                      state.selectedService,
                      state.sortBy.apiValue,
                      state.sortOrder.apiValue,
                      forceRefresh,
                  )
                }
                .onSuccess {
                  log.i { "创作者搜索 -> 成功(queryLength=${state.query.length},count=${it.size})" }
                  mutableState.value = mutableState.value.copy(creators = it, isLoading = false)
                }
                .onFailure {
                  if (it is CancellationException) return@launch
                  log.e(it) { "创作者搜索 -> 失败" }
                  mutableState.value =
                      mutableState.value.copy(isLoading = false, errorMessage = it.message)
                }
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }
}
