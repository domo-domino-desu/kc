package ddd.kc.ui.pages.tags

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Tag
import ddd.kc.data.repository.TagRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

data class TagsState(
    val allTags: List<Tag> = emptyList(),
    val filter: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
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
    if (!forceRefresh && loadedPlatform == platform && mutableState.value.allTags.isNotEmpty())
        return
    val filter = mutableState.value.filter
    mutableState.value = TagsState(filter = filter)
    mutableState.value = mutableState.value.copy(isLoading = true)
    screenModelScope.launch {
      log.i { "Tags页面 -> 加载开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
      runCatching { tagRepo.getAllTags(platform, forceRefresh) }
          .onSuccess {
            log.i { "Tags页面 -> 加载成功(platform=${platform.name},count=${it.size})" }
            loadedPlatform = platform
            mutableState.value = mutableState.value.copy(allTags = it, isLoading = false)
          }
          .onFailure {
            log.e(it) { "Tags页面 -> 加载失败(platform=${platform.name})" }
            mutableState.value =
                mutableState.value.copy(isLoading = false, errorMessage = it.message)
          }
    }
  }

  fun onFilterChanged(filter: String) {
    mutableState.value = mutableState.value.copy(filter = filter)
  }
}
