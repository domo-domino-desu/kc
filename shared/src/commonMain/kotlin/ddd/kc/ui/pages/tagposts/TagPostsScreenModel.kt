package ddd.kc.ui.pages.tagposts

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.key
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.ui.components.paging.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.paging.OffsetPagingMachine
import ddd.kc.ui.components.paging.OffsetPagingState
import ddd.kc.ui.components.paging.PagingAnchor
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = DEFAULT_PAGE_SIZE

class TagPostsScreenModel(
    private val postRepo: PostRepository,
    private val tag: String,
) : StateScreenModel<OffsetPagingState<Post>>(OffsetPagingState()) {

  private val reducer = OffsetPagingMachine<Post, PostKey> { it.key }
  private val log = KcLog.withTag("TagPostsScreenModel")
  private var generation = 0L

  fun load(forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.items.isNotEmpty()) return
    val requestGeneration = ++generation
    screenModelScope.launch {
      log.i { "Tag Posts -> 首屏开始(tagLength=${tag.length},forceRefresh=$forceRefresh)" }
      mutableState.value = reducer.beginLoad(mutableState.value, forceRefresh)
      resultOfSuspend { postRepo.getPostsByTagPage(tag, 0, forceRefresh) }
          .onSuccess { page ->
            if (requestGeneration != generation) return@onSuccess
            val posts = page.items
            log.i { "Tag Posts -> 首屏成功(count=${posts.size})" }
            mutableState.value =
                reducer.reduceFirstPage(
                    mutableState.value,
                    posts,
                    page.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                    posts.size,
                    page.pageInfo,
                )
          }
          .onFailure {
            if (requestGeneration != generation) return@onFailure
            log.e(it) { "Tag Posts -> 首屏失败" }
            mutableState.value = reducer.reduceFirstPageError(mutableState.value, it)
          }
    }
  }

  fun loadMore() {
    if (!reducer.canLoadMore(mutableState.value)) return
    val requestGeneration = ++generation
    screenModelScope.launch {
      log.i { "Tag Posts -> 追加开始(tagLength=${tag.length},offset=${mutableState.value.offset})" }
      mutableState.value = reducer.beginAppend(mutableState.value)
      val offset = mutableState.value.offset
      resultOfSuspend { postRepo.getPostsByTagPage(tag, offset) }
          .onSuccess { page ->
            if (requestGeneration != generation) return@onSuccess
            val posts = page.items
            log.i { "Tag Posts -> 追加成功(count=${posts.size})" }
            mutableState.value =
                reducer.reduceAppend(
                    mutableState.value,
                    posts,
                    page.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                    offset + posts.size,
                    null,
                )
          }
          .onFailure {
            if (requestGeneration != generation) return@onFailure
            log.e(it) { "Tag Posts -> 追加失败" }
            mutableState.value = reducer.reduceAppendError(mutableState.value, it)
          }
    }
  }

  fun loadPrevious() {
    val current = mutableState.value
    if (!reducer.canLoadPrevious(current)) return
    val offset = (current.startOffset - PAGE_SIZE).coerceAtLeast(0)
    val requestGeneration = ++generation
    screenModelScope.launch {
      mutableState.value = reducer.beginPrepend(mutableState.value)
      resultOfSuspend { postRepo.getPostsByTagPage(tag, offset) }
          .onSuccess { page ->
            if (requestGeneration != generation) return@onSuccess
            val posts = page.items
            mutableState.value =
                reducer.reducePrepend(
                    mutableState.value,
                    posts,
                    mutableState.value.hasMore,
                    offset,
                    null,
                )
          }
          .onFailure {
            if (requestGeneration != generation) return@onFailure
            mutableState.value = reducer.reducePrependError(mutableState.value, it)
          }
    }
  }

  fun jumpToPage(page: Int) {
    val previous = mutableState.value
    val targetPage = page.coerceIn(1, previous.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    val requestGeneration = ++generation
    screenModelScope.launch {
      mutableState.value = reducer.beginJump(mutableState.value, offset)
      resultOfSuspend { postRepo.getPostsByTagPage(tag, offset) }
          .onSuccess { pageResult ->
            if (requestGeneration != generation) return@onSuccess
            val posts = pageResult.items
            mutableState.value =
                reducer.reduceFirstPage(
                    mutableState.value,
                    posts,
                    pageResult.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                    offset + posts.size,
                    pageResult.pageInfo,
                )
          }
          .onFailure {
            if (requestGeneration != generation) return@onFailure
            mutableState.value = reducer.reduceJumpError(previous, it)
          }
    }
  }

  fun onViewportChanged(anchor: PagingAnchor) {
    mutableState.value =
        reducer.updateViewport(
            mutableState.value,
            firstVisibleItemIndex = anchor.index,
            firstVisibleItemScrollOffset = anchor.offset,
            anchorKey = anchor.itemKey,
            pageSize = PAGE_SIZE,
        )
  }
}
