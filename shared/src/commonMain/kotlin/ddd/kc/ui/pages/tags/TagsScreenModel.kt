package ddd.kc.ui.pages.tags

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.Tag
import ddd.kc.data.model.preserveRefreshUi
import ddd.kc.data.repository.TagRepository
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class TagsState(
    val result: QueryState<List<Tag>> = QueryState(isLoading = true),
    val allTags: List<Tag> = emptyList(),
    val filter: String = "",
) {
  val isLoading: Boolean
    get() = result.isLoading

  val error: ddd.kc.data.model.QueryError?
    get() = result.error

  val filteredTags: List<Tag>
    get() =
        if (filter.isBlank()) allTags
        else allTags.filter { it.tag.contains(filter, ignoreCase = true) }
}

class TagsScreenModel(
    private val tagRepo: TagRepository,
) : StateScreenModel<TagsState>(TagsState()) {
  private val log = KcLog.withTag("TagsScreenModel")
  private var loaded = false
  private var loadJob: Job? = null
  private var generation: Long = 0

  fun load(forceRefresh: Boolean = false) {
    if (
        !forceRefresh &&
            loaded &&
            mutableState.value.result.data != null &&
            !mutableState.value.result.isStale
    )
        return
    loadJob?.cancel()
    generation++
    val requestGeneration = generation
    val filter = mutableState.value.filter
    mutableState.value =
        mutableState.value.copy(
            filter = filter,
            result =
                mutableState.value.result.copy(
                    isLoading = mutableState.value.allTags.isEmpty(),
                    isRefreshing = mutableState.value.allTags.isNotEmpty(),
                    error = null,
                ),
        )
    loadJob =
        screenModelScope.launch {
          log.i { "Tags页面 -> 加载开始(forceRefresh=$forceRefresh)" }
          tagRepo.observeTags(forceRefresh).collect {
            if (requestGeneration != generation) return@collect
            val tags = it.data ?: mutableState.value.allTags
            val result = it.preserveRefreshUi(tags.isNotEmpty())
            if (it.data != null) {
              log.i { "Tags页面 -> 加载成功(count=${tags.size})" }
              loaded = true
            }
            mutableState.value = mutableState.value.copy(result = result, allTags = tags)
          }
        }
  }

  fun onFilterChanged(filter: String) {
    mutableState.value = mutableState.value.copy(filter = filter)
  }
}
