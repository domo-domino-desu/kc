package ddd.kc.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.AppDatabaseBuilderFactory
import ddd.kc.data.local.MIGRATION_1_2
import ddd.kc.di.KOIN_QUALIFIER_SESSION_VAULT
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun desktopPlatformModule(): Module = module {
  single<AppDatabaseBuilderFactory> {
    AppDatabaseBuilderFactory {
      val dbFile = File(applicationDataDirectory(), "kc.db")
      dbFile.parentFile?.mkdirs()
      Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
          .setDriver(BundledSQLiteDriver())
          .addMigrations(MIGRATION_1_2)
    }
  }
  single<DataStore<Preferences>> {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
          val f =
              File(System.getProperty("user.home"), ".config/kc/datastore/settings.preferences_pb")
          f.parentFile?.mkdirs()
          f.absolutePath.toPath()
        }
    )
  }
  single(named(KOIN_QUALIFIER_SESSION_VAULT)) { KSafe(fileName = KOIN_QUALIFIER_SESSION_VAULT) }
}

private fun applicationDataDirectory(): File {
  val home = System.getProperty("user.home")
  val os = System.getProperty("os.name").lowercase()
  return when {
    os.contains("win") -> File(System.getenv("APPDATA") ?: home, "kc")
    os.contains("mac") -> File(home, "Library/Application Support/kc")
    else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "kc")
  }
}
