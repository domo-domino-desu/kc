package ddd.kc.data.model

import kotlinx.serialization.Serializable

/** Stable Pawchive identity. A creator id is only unique within its service. */
@Serializable data class CreatorKey(val service: String, val id: String)

/** Stable Pawchive identity. A post id is only unique within its creator and service. */
@Serializable
data class PostKey(
    val service: String,
    val creatorId: String,
    val id: String,
)

/** Stable DM identity without falling back to mutable message content. */
data class DmKey(
    val service: String?,
    val creatorId: String?,
    val hash: String?,
    val id: String?,
    val added: String?,
)

val Creator.key: CreatorKey
  get() = CreatorKey(service, id)

val Post.key: PostKey
  get() = PostKey(service, creatorId, id)

val Post.creatorKey: CreatorKey
  get() = CreatorKey(service, creatorId)

val DM.key: DmKey
  get() = DmKey(service = service, creatorId = user, hash = hash, id = id, added = added)
