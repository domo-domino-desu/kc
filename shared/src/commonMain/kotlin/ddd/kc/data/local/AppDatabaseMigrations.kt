package ddd.kc.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

val MIGRATION_1_2 =
    object : Migration(1, 2) {
      override fun migrate(connection: SQLiteConnection) {
        // Query responses are disposable. Recreating this table also removes the old chunk table
        // without spending startup time joining large JSON values back together.
        connection.execute("DROP TABLE IF EXISTS kc_cache_body_chunk")
        connection.execute("DROP TABLE IF EXISTS kc_cache")
        connection.execute(
            "CREATE TABLE IF NOT EXISTS kc_cache " +
                "(cacheKey TEXT NOT NULL, body TEXT NOT NULL, cachedAtMs INTEGER NOT NULL, " +
                "PRIMARY KEY(cacheKey))"
        )
        connection.execute(
            "CREATE TABLE IF NOT EXISTS kc_creator " +
                "(service TEXT NOT NULL, creatorId TEXT NOT NULL, name TEXT NOT NULL, " +
                "indexed INTEGER NOT NULL, updated INTEGER NOT NULL, favorited INTEGER NOT NULL, " +
                "publicId TEXT, PRIMARY KEY(service, creatorId))"
        )
        connection.execute(
            "CREATE TABLE IF NOT EXISTS kc_creator_sync " +
                "(syncKey TEXT NOT NULL, cachedAtMs INTEGER NOT NULL, PRIMARY KEY(syncKey))"
        )
      }
    }

private fun SQLiteConnection.execute(sql: String) {
  prepare(sql).use { it.step() }
}
