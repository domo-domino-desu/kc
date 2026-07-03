package ddd.kc.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.AppDatabaseBuilderFactory
import ddd.kc.di.KOIN_QUALIFIER_SESSION_VAULT
import ddd.kc.ui.platform.PlatformShortcutManager
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun desktopPlatformModule(): Module = module {
  single<AppDatabaseBuilderFactory> {
    AppDatabaseBuilderFactory {
      val dbFile = File(System.getProperty("user.home"), ".cache/kc/room-cache/kc-cache.db")
      dbFile.parentFile?.mkdirs()
      Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath).setDriver(BundledSQLiteDriver())
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
  single { PlatformShortcutManager() }
}
