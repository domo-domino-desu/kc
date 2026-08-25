package ddd.kc.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class SearchKind {
  POSTS,
  DMS,
}

data class SearchHistoryRecord(
    val kind: SearchKind,
    val query: String,
)
