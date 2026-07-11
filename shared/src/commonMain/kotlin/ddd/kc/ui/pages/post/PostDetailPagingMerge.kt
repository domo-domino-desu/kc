package ddd.kc.ui.pages.post

import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.Post
import ddd.kc.data.model.key

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
        posts = (currentPosts + pagePosts).distinctBy { it.key },
        offset = nextOffset + pagePosts.size,
        hasMore = pageInfo?.hasNext ?: (pagePosts.size >= pageSize),
    )

internal fun prependDetailPosts(
    currentPosts: List<Post>,
    currentIndex: Int,
    previousOffset: Int,
    pagePosts: List<Post>,
): DetailPrependResult {
  val existingKeys = currentPosts.map { it.key }.toSet()
  val incoming = pagePosts.filterNot { it.key in existingKeys }
  return DetailPrependResult(
      posts = incoming + currentPosts,
      currentIndex = currentIndex + incoming.size,
      startOffset = previousOffset,
  )
}
