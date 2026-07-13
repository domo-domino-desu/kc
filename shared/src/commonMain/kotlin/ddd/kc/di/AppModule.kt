package ddd.kc.di

import ddd.kc.data.local.AppDatabase
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named

const val KOIN_QUALIFIER_SESSION_VAULT = "session_vault"
const val KOIN_QUALIFIER_CACHED_IMAGE_CLIENT = "cached_image_client"
const val KOIN_QUALIFIER_PAWCHIVE_CLIENT = "pawchive_client"

fun appModules(platformModule: Module): List<Module> =
    listOf(
        dataModule(),
        uiModule(),
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
  runCatching { koin.get<HttpClient>(named(KOIN_QUALIFIER_PAWCHIVE_CLIENT)).close() }
  runCatching { koin.get<HttpClient>(named(KOIN_QUALIFIER_CACHED_IMAGE_CLIENT)).close() }
  runCatching { koin.get<AppDatabase>().close() }
  stopKoin()
}
