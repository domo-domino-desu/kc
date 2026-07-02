package ddd.kc.ui.pages.creator

import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DiscordChannel
import ddd.kc.data.model.Post
import ddd.kc.data.model.Tag
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.PaginationSnapshot

data class CreatorPagerUiState(
    val creators: List<Creator> = emptyList(),
    val currentIndex: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val favoriteCreatorIds: Set<String> = emptySet(),
    val favoriteErrorMessage: String? = null,
    val loadingCreatorPostIds: Set<String> = emptySet(),
    val loadingCreatorAnnouncementIds: Set<String> = emptySet(),
    val loadingCreatorTagIds: Set<String> = emptySet(),
    val loadingRecommendedCreatorIds: Set<String> = emptySet(),
    val loadingCreatorDiscordChannelIds: Set<String> = emptySet(),
    val creatorPostSnapshots: Map<String, PaginationSnapshot<Post>> = emptyMap(),
    val creatorAnnouncements: Map<String, List<Announcement>> = emptyMap(),
    val creatorTags: Map<String, List<Tag>> = emptyMap(),
    val creatorLinks: Map<String, List<Creator>> = emptyMap(),
    val recommendedCreators: Map<String, List<Creator>> = emptyMap(),
    val creatorDiscordChannels: Map<String, List<DiscordChannel>> = emptyMap(),
    /** creatorId → (announcementKey → translated content state) */
    val announcementTranslations: Map<String, Map<String, ContentTranslationState>> = emptyMap(),
) {
  val hasPrevious: Boolean
    get() = currentIndex > 0

  val hasNext: Boolean
    get() = currentIndex < creators.size - 1 || hasMore

  val currentCreator: Creator?
    get() = creators.getOrNull(currentIndex)
}
