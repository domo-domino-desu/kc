package ddd.kc.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ddd.kc.data.model.Creator

@Entity(tableName = "kc_creator", primaryKeys = ["service", "creatorId"])
data class CreatorEntity(
    val service: String,
    val creatorId: String,
    val name: String,
    val indexed: Long,
    val updated: Long,
    val favorited: Int,
    val publicId: String?,
)

@Entity(tableName = "kc_creator_sync")
data class CreatorSyncEntity(
    @PrimaryKey val syncKey: String,
    val cachedAtMs: Long,
)

fun Creator.toEntity(): CreatorEntity =
    CreatorEntity(service, id, name, indexed, updated, favorited, publicId)

fun CreatorEntity.toModel(): Creator =
    Creator(creatorId, name, service, indexed, updated, favorited, publicId)
