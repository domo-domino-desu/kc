package ddd.kc.ui.pages.tags

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.Tag
import ddd.kc.data.repository.TagRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

data class TagsState(
    val result: QueryState<List<Tag>> = QueryState(isLoading = true),
    val allTags: List<Tag> = emptyList(),
    val filter: String = "",
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message

  val filteredTags: List<Tag>
    get() =
        if (filter.isBlank()) allTags
        else allTags.filter { it.tag.contains(filter, ignoreCase = true) }
}

class TagsScreenModel(
    private val tagRepo: TagRepository,
) : StateScreenModel<TagsState>(TagsState()) {
  private val log = KcLog.withTag("TagsScreenModel")
  private var loadedPlatform: Platform? = null

  fun load(platform: Platform, forceRefresh: Boolean = false) {
    if (
        !forceRefresh &&
            loadedPlatform == platform &&
            mutableState.value.result.data != null &&
            !mutableState.value.result.isStale
    )
        return
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
    screenModelScope.launch {
      log.i { "Tags页面 -> 加载开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
      tagRepo.observeTags(forceRefresh).collect {
        val tags = it.data ?: mutableState.value.allTags
        if (it.data != null) {
          log.i { "Tags页面 -> 加载成功(platform=${platform.name},count=${tags.size})" }
          loadedPlatform = platform
        }
        mutableState.value = mutableState.value.copy(result = it, allTags = tags)
      }
    }
  }

  fun onFilterChanged(filter: String) {
    mutableState.value = mutableState.value.copy(filter = filter)
  }
}
