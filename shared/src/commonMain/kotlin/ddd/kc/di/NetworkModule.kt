package ddd.kc.di

import ddd.kc.data.media.ImageProgressTracker
import ddd.kc.data.media.createCachedImageHttpClient
import ddd.kc.data.network.KcApiClient
import ddd.kc.data.network.KcSessionStore
import ddd.kc.data.network.buildKcHttpClient
import ddd.kc.data.settings.AppSettings
import ddd.kc.data.translation.KtorTranslationPort
import ddd.kc.data.translation.TranslationEngine
import ddd.kc.data.translation.TranslationPort
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun networkModule() = module {
  single { AcceptAllCookiesStorage() }
  single { KcSessionStore(get(named(KOIN_QUALIFIER_SESSION_VAULT))) }
  single { ImageProgressTracker() }
  single(named(KOIN_QUALIFIER_CACHED_IMAGE_CLIENT)) { createCachedImageHttpClient(get()) }
  single { buildKcHttpClient(get()) }
  single { AppSettings(get()) }
  single { KcApiClient(get(), get(), get(), get()) }
  single<TranslationPort> { KtorTranslationPort(get()) }
  single { TranslationEngine(get(), get()) }
}
