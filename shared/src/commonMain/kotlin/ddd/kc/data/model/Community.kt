package ddd.kc.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CommunityLounge(val id: String, val name: String, val messageCount: Int = 0)

@Serializable data class CommunityReply(val author: String, val text: String)

@Serializable
data class CommunityEmbed(
    val url: String,
    val site: String? = null,
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class CommunityMessage(
    val key: String,
    val startsGroup: Boolean,
    val author: String? = null,
    val published: String? = null,
    val avatarUrl: String? = null,
    val avatarInitial: String? = null,
    val avatarColor: String? = null,
    val content: String = "",
    val isCreator: Boolean = false,
    val isDeleted: Boolean = false,
    val reply: CommunityReply? = null,
    val imageUrl: String? = null,
    val imageAlt: String? = null,
    val embed: CommunityEmbed? = null,
)

@Serializable
data class CommunityPage(
    val lounges: List<CommunityLounge>,
    val selectedLoungeId: String,
    val messages: List<CommunityMessage>,
    val pageInfo: PageInfo? = null,
)
