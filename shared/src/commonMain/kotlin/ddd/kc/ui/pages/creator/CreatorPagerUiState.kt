package ddd.kc.ui.pages.creator

import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryError
import ddd.kc.data.model.Tag
import ddd.kc.ui.components.state.ContentTranslationState
import ddd.kc.ui.components.state.PaginationSnapshot

data class CreatorPagerUiState(
    val creators: List<Creator> = emptyList(),
    val currentIndex: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val favoriteCreatorIds: Set<CreatorKey> = emptySet(),
    val favoriteError: QueryError? = null,
    val announcementErrors: Map<CreatorKey, QueryError> = emptyMap(),
    val tagErrors: Map<CreatorKey, QueryError> = emptyMap(),
    val linkErrors: Map<CreatorKey, QueryError> = emptyMap(),
    val loadingCreatorPostIds: Set<CreatorKey> = emptySet(),
    val loadingCreatorAnnouncementIds: Set<CreatorKey> = emptySet(),
    val loadingCreatorTagIds: Set<CreatorKey> = emptySet(),
    val creatorPostSnapshots: Map<CreatorKey, PaginationSnapshot<Post>> = emptyMap(),
    val creatorAnnouncements: Map<CreatorKey, List<Announcement>> = emptyMap(),
    val creatorTags: Map<CreatorKey, List<Tag>> = emptyMap(),
    val creatorLinks: Map<CreatorKey, List<Creator>> = emptyMap(),
    /** creatorId → (announcementKey → translated content state) */
    val announcementTranslations: Map<CreatorKey, Map<String, ContentTranslationState>> =
        emptyMap(),
) {
  val hasPrevious: Boolean
    get() = currentIndex > 0

  val hasNext: Boolean
    get() = currentIndex < creators.size - 1 || hasMore

  val currentCreator: Creator?
    get() = creators.getOrNull(currentIndex)
}
