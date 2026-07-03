package ddd.kc.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kc_creator_history")
data class CreatorHistoryEntity(
    @PrimaryKey val historyKey: String,
    val platform: String,
    val service: String,
    val creatorId: String,
    val creatorJson: String,
    val visitedAtMs: Long,
)
