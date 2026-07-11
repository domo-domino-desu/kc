package ddd.kc.ui.pages.post

import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.QueryError
import ddd.kc.ui.state.ContentTranslationState

data class PostPagerUiState(
    val posts: List<Post> = emptyList(),
    val currentIndex: Int = 0,
    val startOffset: Int = 0,
    val offset: Int = 0,
    val isLoadingPrevious: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val favoritePostIds: Set<PostKey> = emptySet(),
    val favoriteError: QueryError? = null,
    val loadingDetailPostIds: Set<PostKey> = emptySet(),
    /** postId → comments list */
    val postComments: Map<PostKey, List<Comment>> = emptyMap(),
    val postCommentErrors: Map<PostKey, QueryError> = emptyMap(),
    /** "${service}:${creatorId}" → Creator */
    val postCreators: Map<CreatorKey, Creator> = emptyMap(),
    /** postId → translated content state */
    val postTranslations: Map<PostKey, ContentTranslationState> = emptyMap(),
    /** postId → full image URLs requested from thumbnail state */
    val requestedFullImageUrls: Map<PostKey, Set<String>> = emptyMap(),
) {
  val hasPrevious: Boolean
    get() = currentIndex > 0 || startOffset > 0

  val hasNext: Boolean
    get() = currentIndex < posts.size - 1 || hasMore

  val currentPost: Post?
    get() = posts.getOrNull(currentIndex)
}
