package ddd.kc.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kc_post_history")
data class PostHistoryEntity(
    @PrimaryKey val historyKey: String,
    val platform: String,
    val service: String,
    val creatorId: String,
    val postId: String,
    val postJson: String,
    val visitedAtMs: Long,
)
