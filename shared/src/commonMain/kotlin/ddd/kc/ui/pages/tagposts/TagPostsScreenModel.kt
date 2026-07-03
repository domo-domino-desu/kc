package ddd.kc.ui.pages.tagposts

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.repository.PostRepository
import ddd.kc.ui.state.PaginationReducer
import ddd.kc.ui.state.PaginationSnapshot
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

class TagPostsScreenModel(
    private val postRepo: PostRepository,
    private val tag: String,
) : StateScreenModel<PaginationSnapshot<Post>>(PaginationSnapshot()) {

  private val reducer = PaginationReducer<Post, String> { it.id }
  private val log = KcLog.withTag("TagPostsScreenModel")

  fun load(platform: Platform, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.items.isNotEmpty()) return
    screenModelScope.launch {
      log.i {
        "Tag Posts -> 首屏开始(platform=${platform.name},tagLength=${tag.length},forceRefresh=$forceRefresh)"
      }
      mutableState.value = reducer.beginLoad(mutableState.value, forceRefresh)
      runCatching { postRepo.getPostsByTagPage(platform, tag, 0, forceRefresh) }
          .onSuccess { page ->
            val posts = page.items
            log.i { "Tag Posts -> 首屏成功(platform=${platform.name},count=${posts.size})" }
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
            log.e(it) { "Tag Posts -> 首屏失败(platform=${platform.name})" }
            mutableState.value = reducer.reduceFirstPageError(mutableState.value, it)
          }
    }
  }

  fun loadMore(platform: Platform) {
    if (!reducer.canLoadMore(mutableState.value)) return
    screenModelScope.launch {
      log.i {
        "Tag Posts -> 追加开始(platform=${platform.name},tagLength=${tag.length},offset=${mutableState.value.offset})"
      }
      mutableState.value = reducer.beginAppend(mutableState.value)
      val offset = mutableState.value.offset
      runCatching { postRepo.getPostsByTagPage(platform, tag, offset) }
          .onSuccess { page ->
            val posts = page.items
            log.i { "Tag Posts -> 追加成功(platform=${platform.name},count=${posts.size})" }
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
            log.e(it) { "Tag Posts -> 追加失败(platform=${platform.name})" }
            mutableState.value = reducer.reduceAppendError(mutableState.value, it)
          }
    }
  }

  fun loadPrevious(platform: Platform) {
    val current = mutableState.value
    if (!reducer.canLoadPrevious(current)) return
    val offset = (current.startOffset - PAGE_SIZE).coerceAtLeast(0)
    screenModelScope.launch {
      mutableState.value = reducer.beginPrepend(mutableState.value)
      runCatching { postRepo.getPostsByTagPage(platform, tag, offset) }
          .onSuccess { page ->
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
          .onFailure { mutableState.value = reducer.reducePrependError(mutableState.value, it) }
    }
  }

  fun jumpToPage(platform: Platform, page: Int) {
    val previous = mutableState.value
    val targetPage = page.coerceIn(1, previous.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    screenModelScope.launch {
      mutableState.value = reducer.beginJump(mutableState.value, offset)
      runCatching { postRepo.getPostsByTagPage(platform, tag, offset) }
          .onSuccess { pageResult ->
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
          .onFailure { mutableState.value = reducer.reduceJumpError(previous, it) }
    }
  }

  fun onVisiblePostIndex(firstVisiblePostIndex: Int) {
    mutableState.value =
        reducer.updateVisiblePage(
            mutableState.value,
            firstVisibleItemIndex = firstVisiblePostIndex,
            pageSize = PAGE_SIZE,
        )
  }
}
