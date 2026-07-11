package ddd.kc

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.AppDatabaseBuilderFactory
import ddd.kc.di.KOIN_QUALIFIER_SESSION_VAULT
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun androidPlatformModule(context: Context): Module = module {
  single<Context> { context.applicationContext }
  single<AppDatabaseBuilderFactory> {
    val appContext = context.applicationContext
    AppDatabaseBuilderFactory {
      Room.databaseBuilder(
          context = appContext,
          klass = AppDatabase::class.java,
          name = "kc.db",
      )
    }
  }
  single<DataStore<Preferences>> {
    val appContext = context.applicationContext
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
          File(appContext.filesDir, "datastore/kc.preferences_pb")
              .apply { parentFile?.mkdirs() }
              .absolutePath
              .toPath()
        }
    )
  }
  single(named(KOIN_QUALIFIER_SESSION_VAULT)) {
    KSafe(context = context.applicationContext, fileName = KOIN_QUALIFIER_SESSION_VAULT)
  }
}
