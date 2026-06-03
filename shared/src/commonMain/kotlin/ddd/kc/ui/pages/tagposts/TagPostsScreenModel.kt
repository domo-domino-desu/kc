package ddd.kc.ui.pages.tagposts

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.repository.PostRepository
import ddd.kc.ui.state.PaginationReducer
import ddd.kc.ui.state.PaginationSnapshot
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

class TagPostsScreenModel(
    private val postRepo: PostRepository,
    private val tag: String,
) : StateScreenModel<PaginationSnapshot<Post>>(PaginationSnapshot()) {

  private val reducer = PaginationReducer<Post, String> { it.id }
  private val log = KcLog.withTag("TagPostsScreenModel")

  fun load(platform: Platform) {
    if (mutableState.value.items.isNotEmpty()) return
    screenModelScope.launch {
      log.i { "Tag Posts -> 首屏开始(platform=${platform.name},tagLength=${tag.length})" }
      mutableState.value = reducer.beginLoad(mutableState.value, false)
      runCatching { postRepo.getPostsByTag(platform, tag, 0) }
          .onSuccess { posts ->
            log.i { "Tag Posts -> 首屏成功(platform=${platform.name},count=${posts.size})" }
            mutableState.value =
                reducer.reduceFirstPage(
                    mutableState.value,
                    posts,
                    posts.size >= PAGE_SIZE,
                    posts.size,
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
      runCatching { postRepo.getPostsByTag(platform, tag, mutableState.value.offset) }
          .onSuccess { posts ->
            log.i { "Tag Posts -> 追加成功(platform=${platform.name},count=${posts.size})" }
            mutableState.value =
                reducer.reduceAppend(
                    mutableState.value,
                    posts,
                    posts.size >= PAGE_SIZE,
                    mutableState.value.offset + posts.size,
                )
          }
          .onFailure {
            log.e(it) { "Tag Posts -> 追加失败(platform=${platform.name})" }
            mutableState.value = reducer.reduceAppendError(mutableState.value, it)
          }
    }
  }
}
