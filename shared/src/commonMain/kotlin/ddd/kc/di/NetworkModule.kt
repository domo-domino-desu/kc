package ddd.kc.di

import ddd.kc.data.media.ImageProgressTracker
import ddd.kc.data.media.MediaDownloader
import ddd.kc.data.media.createCachedImageHttpClient
import ddd.kc.data.network.KcSessionStore
import ddd.kc.data.network.PawchiveAccountApi
import ddd.kc.data.network.PawchiveApi
import ddd.kc.data.network.PawchiveHttpGateway
import ddd.kc.data.network.buildKcHttpClient
import ddd.kc.data.security.KSafeSecretStore
import ddd.kc.data.security.SecretStore
import ddd.kc.data.settings.AppSettings
import ddd.kc.data.translation.TranslationDispatcher
import ddd.kc.data.translation.TranslationEngine
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun networkModule() = module {
  single { AcceptAllCookiesStorage() }
  single<SecretStore> { KSafeSecretStore(get(named(KOIN_QUALIFIER_SESSION_VAULT))) }
  single { KcSessionStore(get()) }
  single { ImageProgressTracker() }
  single(named(KOIN_QUALIFIER_CACHED_IMAGE_CLIENT)) { createCachedImageHttpClient(get()) }
  single { buildKcHttpClient(get()) }
  single { MediaDownloader(get()) }
  single { AppSettings(get(), get()) }
  single { PawchiveHttpGateway(get(), get(), get()) }
  single { PawchiveAccountApi(get(), get()) }
  single { PawchiveApi(get(), get(), get()) }
  single { TranslationDispatcher(get()) }
  single { TranslationEngine(get(), get()) }
}
