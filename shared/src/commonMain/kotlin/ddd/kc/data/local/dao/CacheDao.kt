package ddd.kc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ddd.kc.data.local.entity.CacheEntity

@Dao
interface CacheDao {
  @Query("SELECT * FROM kc_cache WHERE cacheKey = :key LIMIT 1")
  suspend fun findByKey(key: String): CacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: CacheEntity)

  @Query("DELETE FROM kc_cache WHERE cacheKey = :key") suspend fun delete(key: String)

  @Query("DELETE FROM kc_cache WHERE cacheKey = :key OR cacheKey LIKE :prefix")
  suspend fun deleteKeyAndPrefixed(key: String, prefix: String)

  @Query("DELETE FROM kc_cache WHERE cachedAtMs < :beforeMs")
  suspend fun deleteExpired(beforeMs: Long)

  @Query("DELETE FROM kc_cache") suspend fun deleteAll()
}
