package ddd.kc.data.remote.media

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.remote.network.AuthRequiredException
import ddd.kc.data.remote.network.PawchiveApiException
import ddd.kc.data.remote.network.PawchiveCfChallengeException
import ddd.kc.data.remote.network.PawchiveChallengeCancelledException
import ddd.kc.data.remote.network.challenge.PawchiveCfSessionStore
import ddd.kc.data.remote.network.challenge.PawchiveChallengeClassifier
import ddd.kc.data.remote.network.challenge.PawchiveChallengeResolver
import ddd.kc.data.remote.network.challenge.PawchiveChallengeSignal
import ddd.kc.data.remote.network.challenge.PawchiveOriginPolicy
import ddd.kc.di.KOIN_QUALIFIER_CACHED_IMAGE_CLIENT
import ddd.kc.utils.logging.KcLog
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.BodyProgress
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

private val log = KcLog.withTag("CachedImage")

suspend fun HttpClient.downloadMedia(url: String): ByteArray {
  val response = get(url)
  if (response.status == HttpStatusCode.Unauthorized) throw AuthRequiredException()
  if (!response.status.isSuccess()) {
    throw PawchiveApiException(response.status.value, "HTTP ${response.status.value}")
  }
  return response.body()
}

fun createCachedImageHttpClient(
    progressTracker: ImageProgressTracker,
    settings: AppSettings,
    cfSessionStore: PawchiveCfSessionStore,
    challengeResolver: PawchiveChallengeResolver,
): HttpClient {
  val client = createCachedImageClient()
  return configureCachedImageClient(
      client,
      progressTracker,
      settings,
      cfSessionStore,
      challengeResolver,
  )
}

fun createCachedImageHttpClient(
    engine: HttpClientEngine,
    progressTracker: ImageProgressTracker,
    settings: AppSettings,
    cfSessionStore: PawchiveCfSessionStore,
    challengeResolver: PawchiveChallengeResolver,
): HttpClient {
  val client = createCachedImageClient(engine)
  return configureCachedImageClient(
      client,
      progressTracker,
      settings,
      cfSessionStore,
      challengeResolver,
  )
}

private fun createCachedImageClient(engine: HttpClientEngine? = null): HttpClient =
    if (engine == null) {
      HttpClient {
        expectSuccess = false
        install(HttpCache)
        install(BodyProgress)
      }
    } else {
      HttpClient(engine) {
        expectSuccess = false
        install(HttpCache)
        install(BodyProgress)
      }
    }

private fun configureCachedImageClient(
    client: HttpClient,
    progressTracker: ImageProgressTracker,
    settings: AppSettings,
    cfSessionStore: PawchiveCfSessionStore,
    challengeResolver: PawchiveChallengeResolver,
): HttpClient {
  val deduplicator = InFlightImageRequestDeduplicator()
  return client.installCachedImageInterceptors(
      progressTracker,
      deduplicator,
      settings,
      cfSessionStore,
      challengeResolver,
  )
}

private fun HttpClient.installCachedImageInterceptors(
    progressTracker: ImageProgressTracker,
    deduplicator: InFlightImageRequestDeduplicator,
    settings: AppSettings,
    cfSessionStore: PawchiveCfSessionStore,
    challengeResolver: PawchiveChallengeResolver,
): HttpClient {
  plugin(HttpSend).intercept { request ->
    val progressKey = normalizeProgressKey(request.url.toString())
    if (progressKey.isBlank()) return@intercept execute(request)
    deduplicator.awaitOrExecute("${request.method.value} $progressKey") {
      progressTracker.markRequestStarted(progressKey)
      request.onDownload { bytesRead, totalBytes ->
        progressTracker.updateDownloadProgress(progressKey, bytesRead, totalBytes ?: -1L)
      }
      try {
        suspend fun executeAttempt(challengeRetryCount: Int): HttpClientCall {
          val siteOrigin = PawchiveOriginPolicy.normalizedSiteOrigin(settings.baseUrl()).orEmpty()
          val trusted = PawchiveOriginPolicy.isTrustedRequest(request.url.toString(), siteOrigin)
          val context = if (trusted) cfSessionStore.load(siteOrigin) else null
          request.headers.remove(HttpHeaders.Cookie)
          if (trusted && !context?.cookieHeader.isNullOrBlank()) {
            request.headers[HttpHeaders.Cookie] = context.cookieHeader
          }
          request.headers[HttpHeaders.UserAgent] =
              context?.userAgent ?: "Mozilla/5.0 (compatible; KC/1.0)"
          if (request.headers[HttpHeaders.Accept].isNullOrBlank()) {
            request.header(HttpHeaders.Accept, "*/*")
          }
          if (request.headers[HttpHeaders.AcceptLanguage].isNullOrBlank()) {
            request.header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
          }
          if (trusted && request.headers[HttpHeaders.Referrer].isNullOrBlank()) {
            request.header(HttpHeaders.Referrer, "$siteOrigin/")
          }

          val call = execute(request).save()
          if (trusted) {
            cfSessionStore.mergeSetCookieHeaders(
                siteOrigin,
                call.response.headers.getAll(HttpHeaders.SetCookie).orEmpty(),
            )
          }
          val status = call.response.status.value
          val contentType = call.response.headers[HttpHeaders.ContentType].orEmpty().lowercase()
          if (status in setOf(403, 429, 503) && contentType.contains("html")) {
            val body = call.response.body<ByteArray>().decodeToString()
            val challenge =
                PawchiveChallengeClassifier.classify(status, call.response.headers, body)
            if (challenge != null) {
              if (challengeRetryCount >= 1) {
                throw PawchiveCfChallengeException(challenge.cfRay)
              }
              val resolved =
                  challengeResolver.awaitResolution(
                      PawchiveChallengeSignal(
                          requestUrl = request.url.toString(),
                          siteOrigin = siteOrigin,
                          cfRay = challenge.cfRay,
                      )
                  )
              if (!resolved) throw PawchiveChallengeCancelledException()
              return executeAttempt(challengeRetryCount + 1)
            }
          }
          return call
        }

        executeAttempt(challengeRetryCount = 0).also { call ->
          log.d {
            "图片缓存请求结束(status=${call.response.status.value},contentLength=${call.response.headers[HttpHeaders.ContentLength].orEmpty().ifBlank { "-" }},urlLength=${progressKey.length})"
          }
        }
      } catch (error: Throwable) {
        when {
          error is CancellationException -> log.d { "图片缓存请求取消(urlLength=${progressKey.length})" }
          progressKey.isExpectedMissingMediaUrl() ->
              log.d(error) { "图片缓存请求失败(urlLength=${progressKey.length})" }
          else -> log.w(error) { "图片缓存请求失败(urlLength=${progressKey.length})" }
        }
        throw error
      }
    }
  }
  return this
}

private fun String.isExpectedMissingMediaUrl(): Boolean =
    contains("/thumbnail/data/") || contains("/banners/")

@OptIn(ExperimentalCoilApi::class)
fun ImageLoader.Builder.installCoilImageProgressSupport(
    cachedImageClient: HttpClient
): ImageLoader.Builder = components {
  add(KtorNetworkFetcherFactory(httpClient = cachedImageClient))
}

fun ImageLoader.Builder.installKcCoilImageProgressSupport(): ImageLoader.Builder {
  val cachedImageClient =
      GlobalContext.get().get<HttpClient>(qualifier = named(KOIN_QUALIFIER_CACHED_IMAGE_CLIENT))
  return installCoilImageProgressSupport(cachedImageClient = cachedImageClient)
}

private class InFlightImageRequestDeduplicator {
  private val mutex = Mutex()
  private val callsByKey = mutableMapOf<String, CompletableDeferred<HttpClientCall>>()

  suspend fun awaitOrExecute(
      deduplicationKey: String,
      executeRequest: suspend () -> HttpClientCall,
  ): HttpClientCall {
    mutex
        .withLock { callsByKey[deduplicationKey] }
        ?.let {
          return it.await()
        }
    val owner = CompletableDeferred<HttpClientCall>()
    val racing =
        mutex.withLock {
          callsByKey[deduplicationKey]
              ?: run {
                callsByKey[deduplicationKey] = owner
                null
              }
        }
    if (racing != null) return racing.await()
    return try {
      executeRequest().also(owner::complete)
    } catch (error: Throwable) {
      owner.completeExceptionally(error)
      throw error
    } finally {
      mutex.withLock { callsByKey.remove(deduplicationKey, owner) }
    }
  }
}
