package ddd.kc.data.local.entity

import androidx.room.Entity

@Entity(tableName = "kc_search_history", primaryKeys = ["kind", "queryKey"])
data class SearchHistoryEntity(
    val kind: String,
    val queryKey: String,
    val query: String,
    val visitedAtMs: Long,
)
