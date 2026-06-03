package ddd.kc.ui.pages.recent

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.ui.state.PaginationReducer
import ddd.kc.ui.state.PaginationSnapshot
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

class RecentDMsScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<PaginationSnapshot<DM>>(PaginationSnapshot()) {

  private val log = KcLog.withTag("RecentDMsScreenModel")
  private val reducer = PaginationReducer<DM, String> { it.hash ?: it.content.orEmpty() }
  private var platform = Platform.KEMONO

  fun load(platform: Platform, forceRefresh: Boolean = false) {
    val platformChanged = this.platform != platform
    this.platform = platform
    if (!forceRefresh && !platformChanged && mutableState.value.items.isNotEmpty()) return
    screenModelScope.launch {
      log.i { "最近DMs -> 首屏开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
      mutableState.value = reducer.beginLoad(mutableState.value, forceRefresh)
      fetch(0, forceRefresh, firstPage = true)
    }
  }

  fun loadMore() {
    if (!reducer.canLoadMore(mutableState.value)) return
    screenModelScope.launch {
      log.i { "最近DMs -> 追加开始(platform=${platform.name},offset=${mutableState.value.offset})" }
      mutableState.value = reducer.beginAppend(mutableState.value)
      fetch(mutableState.value.offset, false, firstPage = false)
    }
  }

  private suspend fun fetch(offset: Int, forceRefresh: Boolean, firstPage: Boolean) {
    runCatching { creatorRepo.getRecentDMs(platform, offset) }
        .onSuccess { dms ->
          val hasMore = dms.size >= PAGE_SIZE
          val nextOffset = offset + dms.size
          log.i {
            "最近DMs -> 加载成功(platform=${platform.name},offset=$offset,count=${dms.size},hasMore=$hasMore)"
          }
          mutableState.value =
              if (firstPage) {
                reducer.reduceFirstPage(mutableState.value, dms, hasMore, nextOffset)
              } else {
                reducer.reduceAppend(mutableState.value, dms, hasMore, nextOffset)
              }
        }
        .onFailure { error ->
          log.e(error) {
            "最近DMs -> 加载失败(platform=${platform.name},offset=$offset,firstPage=$firstPage)"
          }
          mutableState.value =
              if (firstPage) {
                reducer.reduceFirstPageError(mutableState.value, error)
              } else {
                reducer.reduceAppendError(mutableState.value, error)
              }
        }
  }
}
