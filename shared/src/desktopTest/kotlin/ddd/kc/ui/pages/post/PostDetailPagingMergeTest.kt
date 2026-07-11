package ddd.kc.ui.pages.post

import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostDetailPagingMergeTest {
  @Test
  fun appendMergesByPostKeyAndUsesPageInfoForHasMore() {
    val result =
        appendDetailPosts(
            currentPosts = listOf(post("1"), post("2")),
            pagePosts = listOf(post("2"), post("3")),
            nextOffset = 50,
            pageInfo = PageInfo(currentPage = 2, lastPage = 2, currentOffset = 50, lastOffset = 50),
            pageSize = 50,
        )

    assertEquals(listOf("1", "2", "3"), result.posts.map { it.id })
    assertEquals(52, result.offset)
    assertFalse(result.hasMore)
  }

  @Test
  fun samePostIdFromDifferentCreatorsIsNotCollapsed() {
    val result =
        appendDetailPosts(
            currentPosts = listOf(post("1", creator = "alice")),
            pagePosts =
                listOf(
                    post("1", creator = "bob"),
                    post("1", creator = "alice", service = "fanbox"),
                ),
            nextOffset = 1,
            pageInfo = null,
            pageSize = 50,
        )

    assertEquals(3, result.posts.size)
  }

  @Test
  fun appendFallsBackToPageSizeWhenPageInfoMissing() {
    val result =
        appendDetailPosts(
            currentPosts = emptyList(),
            pagePosts = List(50) { post("$it") },
            nextOffset = 0,
            pageInfo = null,
            pageSize = 50,
        )

    assertTrue(result.hasMore)
    assertEquals(50, result.offset)
  }

  @Test
  fun prependKeepsCurrentPostSelectedAfterInsertingNewItems() {
    val result =
        prependDetailPosts(
            currentPosts = listOf(post("50"), post("51")),
            currentIndex = 1,
            previousOffset = 0,
            pagePosts = listOf(post("48"), post("49"), post("50")),
        )

    assertEquals(listOf("48", "49", "50", "51"), result.posts.map { it.id })
    assertEquals(3, result.currentIndex)
    assertEquals("51", result.posts[result.currentIndex].id)
    assertEquals(0, result.startOffset)
  }

  private fun post(
      id: String,
      creator: String = "creator",
      service: String = "patreon",
  ): Post = Post(id = id, user = creator, service = service)
}
