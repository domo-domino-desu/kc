package ddd.kc.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity

@Database(
    entities = [CacheEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun cacheDao(): CacheDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

fun interface AppDatabaseBuilderFactory {
  fun create(): RoomDatabase.Builder<AppDatabase>
}
