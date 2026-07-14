package ddd.kc.di

import ddd.kc.data.local.ActivityHistoryRepository
import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.AppDatabaseBuilderFactory
import ddd.kc.data.local.security.KSafeSecretStore
import ddd.kc.data.local.security.SecretStore
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.remote.media.ImageProgressTracker
import ddd.kc.data.remote.media.createCachedImageHttpClient
import ddd.kc.data.remote.network.KcSessionStore
import ddd.kc.data.remote.network.PawchiveApi
import ddd.kc.data.remote.network.PawchiveHttpGateway
import ddd.kc.data.remote.network.buildKcHttpClient
import ddd.kc.data.remote.network.challenge.PawchiveCfSessionStore
import ddd.kc.data.remote.network.challenge.PawchiveChallengeController
import ddd.kc.data.remote.network.challenge.PawchiveChallengeCoordinator
import ddd.kc.data.remote.network.challenge.PawchiveChallengeProbeVerifier
import ddd.kc.data.remote.network.challenge.PawchiveChallengeResolver
import ddd.kc.data.remote.network.challenge.PawchiveChallengeSessionStorage
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.data.remote.repository.TagRepository
import ddd.kc.data.remote.translation.TranslationDispatcher
import ddd.kc.data.remote.translation.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun dataModule() = module {
  single {
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      coerceInputValues = true
    }
  }
  single<SecretStore> { KSafeSecretStore(get(named(KOIN_QUALIFIER_SESSION_VAULT))) }
  single { AppSettings(get(), get()) }
  single { KcSessionStore(get()) }
  single { PawchiveCfSessionStore(get(), get()) }
  single { buildKcHttpClient() }
  single(named(KOIN_QUALIFIER_PAWCHIVE_CLIENT)) {
    buildKcHttpClient(
        followRedirects = false,
        useDefaultUserAgent = false,
    )
  }
  single(named(KOIN_QUALIFIER_PAWCHIVE_PROBE_CLIENT)) {
    buildKcHttpClient(
        followRedirects = true,
        useDefaultUserAgent = false,
    )
  }
  single { PawchiveChallengeSessionStorage() }
  single {
    PawchiveChallengeProbeVerifier(
        client = get(named(KOIN_QUALIFIER_PAWCHIVE_PROBE_CLIENT)),
        cfSessionStore = get(),
        loginSessionStore = get(),
    )
  }
  single {
    PawchiveChallengeCoordinator(
        sessionStorage = get(),
        cfSessionStore = get(),
        loginSessionStore = get(),
        probeVerifier = get(),
    )
  }
  single<PawchiveChallengeController> { get<PawchiveChallengeCoordinator>() }
  single<PawchiveChallengeResolver> { get<PawchiveChallengeCoordinator>() }
  single { ImageProgressTracker() }
  single(named(KOIN_QUALIFIER_CACHED_IMAGE_CLIENT)) {
    createCachedImageHttpClient(get(), get(), get(), get())
  }
  single {
    PawchiveHttpGateway(
        get(named(KOIN_QUALIFIER_PAWCHIVE_CLIENT)),
        get(),
        get(),
        get(),
        get(),
    )
  }
  single { PawchiveApi(get(), get()) }
  single { TranslationDispatcher(get()) }
  single { TranslationEngine(get(), get()) }
  single<AppDatabase> { get<AppDatabaseBuilderFactory>().create().build() }
  single { PostRepository(get(), get(), get(), get(), Dispatchers.IO) }
  single { CreatorRepository(get(), get(), get(), Dispatchers.IO) }
  single { TagRepository(get(), get(), get(), Dispatchers.IO) }
  single { ActivityHistoryRepository(get(), get(), Dispatchers.IO) }
}
