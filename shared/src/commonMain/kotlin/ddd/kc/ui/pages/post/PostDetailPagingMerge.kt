package ddd.kc.ui.pages.post

import ddd.kc.data.model.Post
import ddd.kc.data.network.PageInfo

internal data class DetailAppendResult(
    val posts: List<Post>,
    val offset: Int,
    val hasMore: Boolean,
)

internal data class DetailPrependResult(
    val posts: List<Post>,
    val currentIndex: Int,
    val startOffset: Int,
)

internal fun appendDetailPosts(
    currentPosts: List<Post>,
    pagePosts: List<Post>,
    nextOffset: Int,
    pageInfo: PageInfo?,
    pageSize: Int,
): DetailAppendResult =
    DetailAppendResult(
        posts = (currentPosts + pagePosts).distinctBy { it.id },
        offset = nextOffset + pagePosts.size,
        hasMore = pageInfo?.hasNext ?: (pagePosts.size >= pageSize),
    )

internal fun prependDetailPosts(
    currentPosts: List<Post>,
    currentIndex: Int,
    previousOffset: Int,
    pagePosts: List<Post>,
): DetailPrependResult {
  val existingIds = currentPosts.map { it.id }.toSet()
  val incoming = pagePosts.filterNot { it.id in existingIds }
  return DetailPrependResult(
      posts = incoming + currentPosts,
      currentIndex = currentIndex + incoming.size,
      startOffset = previousOffset,
  )
}
