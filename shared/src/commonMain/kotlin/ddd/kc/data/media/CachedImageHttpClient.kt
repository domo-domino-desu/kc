package ddd.kc.data.media

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import ddd.kc.di.KOIN_QUALIFIER_CACHED_IMAGE_CLIENT
import ddd.kc.util.logging.KcLog
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.BodyProgress
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

private val log = KcLog.withTag("CachedImage")

fun createCachedImageHttpClient(progressTracker: ImageProgressTracker): HttpClient {
  val deduplicator = InFlightImageRequestDeduplicator()
  val client = HttpClient {
    expectSuccess = false
    install(HttpCache)
    install(BodyProgress)
  }
  return client.installCachedImageInterceptors(progressTracker, deduplicator)
}

private fun HttpClient.installCachedImageInterceptors(
    progressTracker: ImageProgressTracker,
    deduplicator: InFlightImageRequestDeduplicator,
): HttpClient {
  plugin(HttpSend).intercept { request ->
    val progressKey = normalizeProgressKey(request.url.toString())
    if (progressKey.isBlank()) return@intercept execute(request)
    deduplicator.awaitOrExecute("${request.method.value} $progressKey") {
      if (request.headers[HttpHeaders.UserAgent].isNullOrBlank()) {
        request.header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; KC/1.0)")
      }
      if (request.headers[HttpHeaders.Accept].isNullOrBlank()) {
        request.header(HttpHeaders.Accept, "*/*")
      }
      progressTracker.markRequestStarted(progressKey)
      request.onDownload { bytesRead, totalBytes ->
        progressTracker.updateDownloadProgress(progressKey, bytesRead, totalBytes ?: -1L)
      }
      try {
        execute(request).save().also { call ->
          log.d {
            "图片缓存请求结束(status=${call.response.status.value},contentLength=${call.response.headers[HttpHeaders.ContentLength].orEmpty().ifBlank { "-" }},url=$progressKey)"
          }
        }
      } catch (error: Throwable) {
        when {
          error is CancellationException -> log.d { "图片缓存请求取消(url=$progressKey)" }
          progressKey.isExpectedMissingMediaUrl() -> log.d(error) { "图片缓存请求失败(url=$progressKey)" }
          else -> log.w(error) { "图片缓存请求失败(url=$progressKey)" }
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
