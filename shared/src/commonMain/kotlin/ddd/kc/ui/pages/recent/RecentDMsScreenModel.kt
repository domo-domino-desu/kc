package ddd.kc.ui.pages.recent

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DM
import ddd.kc.data.model.DmKey
import ddd.kc.data.model.key
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.data.remote.repository.awaitData
import ddd.kc.ui.components.paging.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.paging.OffsetPagingMachine
import ddd.kc.ui.components.paging.OffsetPagingState
import ddd.kc.ui.components.paging.PagingAnchor
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = DEFAULT_PAGE_SIZE

class RecentDMsScreenModel(
    private val creatorRepo: CreatorRepository,
) : StateScreenModel<OffsetPagingState<DM>>(OffsetPagingState()) {

  private val log = KcLog.withTag("RecentDMsScreenModel")
  private val reducer = OffsetPagingMachine<DM, DmKey> { it.key }
  private var generation: Long = 0

  fun load(forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.items.isNotEmpty()) return
    generation++
    val requestGeneration = generation
    screenModelScope.launch {
      log.i { "最近DMs -> 首屏开始(forceRefresh=$forceRefresh)" }
      mutableState.value = reducer.beginLoad(mutableState.value, forceRefresh)
      fetch(0, forceRefresh, firstPage = true, requestGeneration = requestGeneration)
    }
  }

  fun loadMore() {
    if (!reducer.canLoadMore(mutableState.value)) return
    screenModelScope.launch {
      log.i { "最近DMs -> 追加开始(offset=${mutableState.value.offset})" }
      mutableState.value = reducer.beginAppend(mutableState.value)
      fetch(mutableState.value.offset, false, firstPage = false)
    }
  }

  fun loadPrevious() {
    val current = mutableState.value
    if (!reducer.canLoadPrevious(current)) return
    val offset = (current.startOffset - PAGE_SIZE).coerceAtLeast(0)
    screenModelScope.launch {
      mutableState.value = reducer.beginPrepend(mutableState.value)
      fetch(offset, forceRefresh = false, firstPage = false, prepend = true)
    }
  }

  fun jumpToPage(page: Int) {
    val previous = mutableState.value
    val targetPage = page.coerceIn(1, previous.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    generation++
    val requestGeneration = generation
    screenModelScope.launch {
      mutableState.value = reducer.beginJump(mutableState.value, offset)
      fetch(
          offset,
          forceRefresh = false,
          firstPage = true,
          rollbackSnapshot = previous,
          requestGeneration = requestGeneration,
      )
    }
  }

  fun onViewportChanged(anchor: PagingAnchor) {
    mutableState.value =
        reducer.updateViewport(
            mutableState.value,
            anchor.index,
            anchor.offset,
            anchor.itemKey,
            PAGE_SIZE,
        )
  }

  fun onNavigationEffectHandled(transactionId: Long) {
    mutableState.value = reducer.consumeNavigationEffect(mutableState.value, transactionId)
  }

  private suspend fun fetch(
      offset: Int,
      forceRefresh: Boolean,
      firstPage: Boolean,
      prepend: Boolean = false,
      rollbackSnapshot: OffsetPagingState<DM>? = null,
      requestGeneration: Long = generation,
  ) {
    resultOfSuspend {
          creatorRepo.observeDmsPage(offset = offset, forceRefresh = forceRefresh).awaitData()
        }
        .onSuccess { page ->
          if (requestGeneration != generation) return@onSuccess
          val dms = page.items
          val hasMore = page.pageInfo?.hasNext ?: (dms.size >= PAGE_SIZE)
          val nextOffset = offset + dms.size
          log.i { "最近DMs -> 加载成功(offset=$offset,count=${dms.size},hasMore=$hasMore)" }
          mutableState.value =
              if (firstPage) {
                reducer.reduceFirstPage(mutableState.value, dms, hasMore, nextOffset, page.pageInfo)
              } else if (prepend) {
                reducer.reducePrepend(
                    mutableState.value,
                    dms,
                    mutableState.value.hasMore,
                    offset,
                    null,
                )
              } else {
                reducer.reduceAppend(mutableState.value, dms, hasMore, nextOffset, null)
              }
        }
        .onFailure { error ->
          if (requestGeneration != generation) return@onFailure
          log.e(error) { "最近DMs -> 加载失败(offset=$offset,firstPage=$firstPage)" }
          mutableState.value =
              if (firstPage) {
                rollbackSnapshot?.let { reducer.reduceJumpError(it, error) }
                    ?: reducer.reduceFirstPageError(mutableState.value, error)
              } else if (prepend) {
                reducer.reducePrependError(mutableState.value, error)
              } else {
                reducer.reduceAppendError(mutableState.value, error)
              }
        }
  }
}
