package ddd.kc.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kc_cache")
data class CacheEntity(
    @PrimaryKey val cacheKey: String,
    val dataJson: String,
    val cachedAtMs: Long,
)
