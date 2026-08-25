package ddd.kc.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ddd.kc.data.local.entity.CreatorHistoryEntity
import ddd.kc.data.local.entity.PostHistoryEntity
import ddd.kc.data.local.entity.SearchHistoryEntity

@Dao
interface HistoryDao {
  @Upsert suspend fun upsertCreator(entity: CreatorHistoryEntity)

  @Query("SELECT * FROM kc_creator_history ORDER BY visitedAtMs DESC LIMIT :limit")
  suspend fun listCreatorsByLatest(limit: Int): List<CreatorHistoryEntity>

  @Query(
      "DELETE FROM kc_creator_history WHERE historyKey NOT IN " +
          "(SELECT historyKey FROM kc_creator_history ORDER BY visitedAtMs DESC LIMIT :limit)"
  )
  suspend fun trimCreators(limit: Int)

  @Query("DELETE FROM kc_creator_history WHERE historyKey = :key")
  suspend fun deleteCreator(key: String)

  @Upsert suspend fun upsertPost(entity: PostHistoryEntity)

  @Query("SELECT * FROM kc_post_history ORDER BY visitedAtMs DESC LIMIT :limit")
  suspend fun listPostsByLatest(limit: Int): List<PostHistoryEntity>

  @Query(
      "DELETE FROM kc_post_history WHERE historyKey NOT IN " +
          "(SELECT historyKey FROM kc_post_history ORDER BY visitedAtMs DESC LIMIT :limit)"
  )
  suspend fun trimPosts(limit: Int)

  @Query("DELETE FROM kc_post_history WHERE historyKey = :key") suspend fun deletePost(key: String)

  @Upsert suspend fun upsertSearch(entity: SearchHistoryEntity)

  @Query(
      "SELECT * FROM kc_search_history WHERE kind = :kind " +
          "ORDER BY visitedAtMs DESC LIMIT :limit"
  )
  suspend fun listSearchesByLatest(kind: String, limit: Int): List<SearchHistoryEntity>

  @Query(
      "DELETE FROM kc_search_history WHERE kind = :kind AND queryKey NOT IN " +
          "(SELECT queryKey FROM kc_search_history WHERE kind = :kind " +
          "ORDER BY visitedAtMs DESC LIMIT :limit)"
  )
  suspend fun trimSearches(kind: String, limit: Int)
}
