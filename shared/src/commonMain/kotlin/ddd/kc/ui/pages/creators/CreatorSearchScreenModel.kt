package ddd.kc.ui.pages.creators

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.QueryState
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
    val result: QueryState<List<Creator>> = QueryState(isLoading = true),
    val creators: List<Creator> = emptyList(),
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message
}

class CreatorSearchScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<CreatorSearchState>(CreatorSearchState()) {
  private var searchJob: Job? = null
  private var platform = Platform.PAWCHIVE

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
            mutableState.value =
                state.copy(
                    result =
                        state.result.copy(
                            isLoading = state.result.data == null,
                            isRefreshing = state.result.data != null,
                            error = null,
                        )
                )
            creatorRepo.observeCreators(forceRefresh).collect { next ->
              val rows =
                  next.data?.let {
                    filterCreators(
                        it,
                        state.query,
                        state.selectedService,
                        state.sortBy,
                        state.sortOrder,
                    )
                  } ?: mutableState.value.creators
              log.i { "创作者搜索 -> 状态(queryLength=${state.query.length},count=${rows.size})" }
              mutableState.value = mutableState.value.copy(result = next, creators = rows)
              if (!next.isLoading && !next.isRefreshing) {
                searchJob = null
              }
            }
          } catch (_: CancellationException) {
            return@launch
          }
        }
  }
}

private fun filterCreators(
    creators: List<Creator>,
    query: String,
    service: String?,
    sortBy: CreatorSort,
    order: SortOrder,
): List<Creator> {
  val normalizedQuery = query.trim()
  val comparator =
      when (sortBy) {
        CreatorSort.FAVORITED -> compareBy<Creator> { it.favorited }
        CreatorSort.INDEXED -> compareBy { it.indexed }
        CreatorSort.UPDATED -> compareBy { it.updated }
        CreatorSort.NAME -> compareBy { it.name.lowercase() }
        CreatorSort.SERVICE ->
            compareBy<Creator> { it.service.lowercase() }.thenBy { it.name.lowercase() }
      }
  val filtered =
      creators
          .asSequence()
          .filter { service == null || it.service.equals(service, ignoreCase = true) }
          .filter {
            normalizedQuery.isBlank() ||
                it.name.contains(normalizedQuery, ignoreCase = true) ||
                it.id.contains(normalizedQuery, ignoreCase = true) ||
                it.publicId?.contains(normalizedQuery, ignoreCase = true) == true
          }
          .toList()
  return filtered.sortedWith(if (order == SortOrder.ASC) comparator else comparator.reversed())
}
