package ddd.kc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "kc_cache")
data class CacheEntity(
    @PrimaryKey val cacheKey: String,
    val cachedAtMs: Long,
)

@Entity(
    tableName = "kc_cache_body_chunk",
    primaryKeys = ["cacheKey", "chunkIndex"],
    foreignKeys =
        [
            ForeignKey(
                entity = CacheEntity::class,
                parentColumns = ["cacheKey"],
                childColumns = ["cacheKey"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("cacheKey")],
)
data class CacheBodyChunkEntity(
    val cacheKey: String,
    val chunkIndex: Int,
    val bodyChunk: String,
)
