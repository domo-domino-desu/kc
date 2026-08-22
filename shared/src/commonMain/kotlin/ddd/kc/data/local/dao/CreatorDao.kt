package ddd.kc.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ddd.kc.data.local.entity.CreatorEntity
import ddd.kc.data.local.entity.CreatorSyncEntity

@Dao
interface CreatorDao {
  @Query("SELECT * FROM kc_creator WHERE service = :service AND creatorId = :creatorId LIMIT 1")
  suspend fun find(service: String, creatorId: String): CreatorEntity?

  @Query("SELECT * FROM kc_creator") suspend fun listAll(): List<CreatorEntity>

  @Query("SELECT * FROM kc_creator_sync WHERE syncKey = :key LIMIT 1")
  suspend fun findSync(key: String): CreatorSyncEntity?

  @Upsert suspend fun upsertAll(entities: List<CreatorEntity>)

  @Delete suspend fun deleteAll(entities: List<CreatorEntity>)

  @Upsert suspend fun upsertSync(entity: CreatorSyncEntity)

  @Transaction
  suspend fun applyDiff(
      upserts: List<CreatorEntity>,
      deletes: List<CreatorEntity>,
      sync: CreatorSyncEntity,
  ) {
    upserts.chunked(500).forEach { upsertAll(it) }
    deletes.chunked(500).forEach { deleteAll(it) }
    upsertSync(sync)
  }
}
