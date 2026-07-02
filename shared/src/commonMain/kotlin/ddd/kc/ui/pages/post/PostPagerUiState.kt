package ddd.kc.ui.pages.post

import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.ui.state.ContentTranslationState

data class PostPagerUiState(
    val posts: List<Post> = emptyList(),
    val currentIndex: Int = 0,
    val startOffset: Int = 0,
    val offset: Int = 0,
    val isLoadingPrevious: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val favoritePostIds: Set<String> = emptySet(),
    val favoriteErrorMessage: String? = null,
    val loadingDetailPostIds: Set<String> = emptySet(),
    /** postId → comments list */
    val postComments: Map<String, List<Comment>> = emptyMap(),
    /** "${service}:${creatorId}" → Creator */
    val postCreators: Map<String, Creator> = emptyMap(),
    /** postId → translated content state */
    val postTranslations: Map<String, ContentTranslationState> = emptyMap(),
    /** postId → full image URLs requested from thumbnail state */
    val requestedFullImageUrls: Map<String, Set<String>> = emptyMap(),
) {
  val hasPrevious: Boolean
    get() = currentIndex > 0 || startOffset > 0

  val hasNext: Boolean
    get() = currentIndex < posts.size - 1 || hasMore

  val currentPost: Post?
    get() = posts.getOrNull(currentIndex)
}
