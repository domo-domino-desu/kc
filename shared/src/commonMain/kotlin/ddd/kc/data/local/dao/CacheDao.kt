package ddd.kc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ddd.kc.data.local.entity.CacheBodyChunkEntity
import ddd.kc.data.local.entity.CacheEntity

private const val CACHE_BODY_CHUNK_CHAR_LIMIT = 128 * 1024

data class CachedBody(
    val body: String,
    val cachedAtMs: Long,
)

@Dao
interface CacheDao {
  @Query("SELECT * FROM kc_cache WHERE cacheKey = :key LIMIT 1")
  suspend fun findByKey(key: String): CacheEntity?

  @Query("SELECT bodyChunk FROM kc_cache_body_chunk WHERE cacheKey = :key ORDER BY chunkIndex ASC")
  suspend fun findBodyChunksByKey(key: String): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: CacheEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBodyChunks(chunks: List<CacheBodyChunkEntity>)

  @Query("DELETE FROM kc_cache_body_chunk WHERE cacheKey = :key")
  suspend fun deleteBodyChunks(key: String)

  @Transaction
  suspend fun findBodyByKey(key: String): CachedBody? {
    val entity = findByKey(key) ?: return null
    val chunks = findBodyChunksByKey(key)
    return CachedBody(body = chunks.joinToString(separator = ""), cachedAtMs = entity.cachedAtMs)
  }

  @Transaction
  suspend fun upsertBody(
      key: String,
      body: String,
      cachedAtMs: Long,
  ) {
    upsert(CacheEntity(key, cachedAtMs))
    deleteBodyChunks(key)
    insertBodyChunks(body.toCacheBodyChunks(key))
  }

  @Query("DELETE FROM kc_cache WHERE cacheKey = :key") suspend fun delete(key: String)

  @Query("DELETE FROM kc_cache WHERE cacheKey = :key OR cacheKey LIKE :prefix")
  suspend fun deleteKeyAndPrefixed(key: String, prefix: String)

  @Query("DELETE FROM kc_cache WHERE cachedAtMs < :beforeMs")
  suspend fun deleteExpired(beforeMs: Long)

  @Query("DELETE FROM kc_cache") suspend fun deleteAll()
}

private fun String.toCacheBodyChunks(cacheKey: String): List<CacheBodyChunkEntity> {
  val chunks = if (isEmpty()) listOf("") else chunked(CACHE_BODY_CHUNK_CHAR_LIMIT)
  return chunks.mapIndexed { index, chunk ->
    CacheBodyChunkEntity(cacheKey = cacheKey, chunkIndex = index, bodyChunk = chunk)
  }
}
