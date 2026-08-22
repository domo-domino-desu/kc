package ddd.kc.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.dao.CreatorDao
import ddd.kc.data.local.dao.HistoryDao
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.local.entity.CreatorEntity
import ddd.kc.data.local.entity.CreatorHistoryEntity
import ddd.kc.data.local.entity.CreatorSyncEntity
import ddd.kc.data.local.entity.PostHistoryEntity

@Database(
    entities =
        [
            CacheEntity::class,
            CreatorEntity::class,
            CreatorSyncEntity::class,
            CreatorHistoryEntity::class,
            PostHistoryEntity::class,
        ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun cacheDao(): CacheDao

  abstract fun creatorDao(): CreatorDao

  abstract fun historyDao(): HistoryDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

fun interface AppDatabaseBuilderFactory {
  fun create(): RoomDatabase.Builder<AppDatabase>
}
