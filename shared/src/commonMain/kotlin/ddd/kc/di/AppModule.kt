package ddd.kc.di

import ddd.kc.data.local.AppDatabase
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

const val KOIN_QUALIFIER_SESSION_VAULT = "session_vault"

fun appModules(platformModule: Module): List<Module> =
    listOf(
        networkModule(),
        databaseModule(),
        repositoryModule(),
        screenModelModule(),
        platformModule,
    )

fun startAppKoin(platformModule: Module): Koin {
  val existing = GlobalContext.getOrNull()
  if (existing != null) return existing
  return startKoin { modules(appModules(platformModule)) }.koin
}

fun stopAppKoin() {
  val koin = GlobalContext.getOrNull() ?: return
  runCatching { koin.get<HttpClient>().close() }
  runCatching { koin.get<AppDatabase>().close() }
  stopKoin()
}
