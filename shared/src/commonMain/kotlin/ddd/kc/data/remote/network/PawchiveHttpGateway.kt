package ddd.kc.data.remote.network

import ddd.kc.data.local.settings.AppSettings
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_REDIRECTS = 5
private val gatewayLog = KcLog.withTag("PawchiveHttpGateway")

class PawchiveHttpGateway(
    private val client: HttpClient,
    private val settings: AppSettings,
    private val sessionStore: KcSessionStore,
) {
  private val endpointMutex = Mutex()

  fun hasSession(): Boolean = sessionStore.hasSession()

  fun clearSession() = sessionStore.clearSession()

  suspend fun getText(
      path: String,
      label: String,
      authenticated: Boolean = false,
      block: HttpRequestBuilder.() -> Unit = {},
  ): String = requestText(HttpMethod.Get, path, label, authenticated, block)

  suspend fun postUnit(
      path: String,
      label: String,
      authenticated: Boolean,
      block: HttpRequestBuilder.() -> Unit = {},
  ) {
    requestText(HttpMethod.Post, path, label, authenticated, block)
  }

  suspend fun deleteUnit(path: String, label: String, authenticated: Boolean) {
    requestText(HttpMethod.Delete, path, label, authenticated) {}
  }

  private suspend fun requestText(
      method: HttpMethod,
      path: String,
      label: String,
      authenticated: Boolean,
      block: HttpRequestBuilder.() -> Unit,
  ): String {
    var baseUrl = currentBaseUrl()
    var directRedirectUrl: Url? = null
    val visitedUrls = mutableSetOf<String>()
    var redirectCount = 0

    while (true) {
      val response =
          client.request {
            this.method = method
            url(baseUrl + path)
            if (authenticated) header(HttpHeaders.Cookie, sessionStore.cookieHeader())
            block()
            directRedirectUrl?.let { url.takeFrom(it.toString()) }
          }
      val requestUrl = response.call.request.url
      if (!visitedUrls.add(requestUrl.toString())) {
        discardBody(response)
        throw redirectFailure(label, response.status, "检测到重定向循环")
      }

      if (!response.status.isRedirectStatus()) return requireSuccess(response, label)
      if (++redirectCount > MAX_REDIRECTS) {
        discardBody(response)
        throw redirectFailure(label, response.status, "重定向次数超过上限")
      }

      val target = parseRedirectTarget(response)
      if (target == null) {
        discardBody(response)
        throw redirectFailure(label, response.status, "Location 不是有效的 HTTPS URL")
      }

      val canonicalOrigin = canonicalRedirectOrigin(requestUrl, target, response.status)
      if (canonicalOrigin != null) {
        discardBody(response)
        baseUrl = adoptCanonicalOrigin(requestUrl.origin(), canonicalOrigin)
        directRedirectUrl = Url(baseUrl + requestUrl.encodedPathAndQuery)
        continue
      }

      if (!canFollowWithoutPersisting(method, requestUrl, target, response.status)) {
        discardBody(response)
        throw redirectFailure(label, response.status, "拒绝非 canonical 重定向")
      }
      discardBody(response)
      directRedirectUrl = target
    }
  }

  private suspend fun currentBaseUrl(): String = endpointMutex.withLock { settings.baseUrl() }

  private suspend fun adoptCanonicalOrigin(
      expectedOrigin: String,
      redirectedOrigin: String,
  ): String =
      endpointMutex.withLock {
        val current = settings.baseUrl()
        if (current != expectedOrigin) return@withLock current
        val updated = settings.updateBaseUrlFromRedirect(expectedOrigin, redirectedOrigin)
        if (!updated) return@withLock settings.baseUrl()
        gatewayLog.i { "Pawchive canonical endpoint 已更新(host=${Url(redirectedOrigin).host})" }
        redirectedOrigin
      }

  private suspend fun requireSuccess(response: HttpResponse, label: String): String {
    if (response.status == HttpStatusCode.Unauthorized) {
      gatewayLog.w { "$label -> 需要登录" }
      throw AuthRequiredException()
    }
    if (!response.status.isSuccess()) {
      val bodyLength = resultOfSuspend { response.bodyAsText() }.getOrDefault("").length
      gatewayLog.w { "$label -> 失败(status=${response.status.value},bodyLength=$bodyLength)" }
      throw PawchiveApiException(response.status.value, "HTTP ${response.status.value}")
    }
    return response.bodyAsText()
  }

  private suspend fun discardBody(response: HttpResponse) {
    resultOfSuspend { response.bodyAsText() }
  }

  private fun redirectFailure(
      label: String,
      status: HttpStatusCode,
      reason: String,
  ): PawchiveApiException {
    gatewayLog.w { "$label -> $reason(status=${status.value})" }
    return PawchiveApiException(status.value, "HTTP ${status.value}: $reason")
  }
}

private fun HttpStatusCode.isRedirectStatus(): Boolean =
    this == HttpStatusCode.MovedPermanently ||
        this == HttpStatusCode.Found ||
        this == HttpStatusCode.SeeOther ||
        this == HttpStatusCode.TemporaryRedirect ||
        this == HttpStatusCode.PermanentRedirect

private fun parseRedirectTarget(response: HttpResponse): Url? {
  val location = response.headers[HttpHeaders.Location]?.trim().orEmpty()
  if (location.isBlank()) return null
  val source = response.call.request.url
  val target =
      runCatching {
            when {
              location.startsWith("/") && !location.startsWith("//") ->
                  Url(source.origin() + location)
              location.startsWith("?") -> Url(source.origin() + source.encodedPath + location)
              else -> Url(location)
            }
          }
          .getOrNull() ?: return null
  return target.takeIf {
    it.protocol == URLProtocol.HTTPS &&
        it.host.isNotBlank() &&
        it.user.isNullOrBlank() &&
        it.password.isNullOrBlank() &&
        it.fragment.isBlank()
  }
}

internal fun canonicalRedirectOrigin(
    source: Url,
    target: Url,
    status: HttpStatusCode,
): String? {
  if (status != HttpStatusCode.MovedPermanently && status != HttpStatusCode.PermanentRedirect) {
    return null
  }
  if (
      target.protocol != URLProtocol.HTTPS ||
          target.host.isBlank() ||
          !target.user.isNullOrBlank() ||
          !target.password.isNullOrBlank() ||
          target.fragment.isNotBlank()
  ) {
    return null
  }
  if (source.origin() == target.origin()) return null
  if (source.encodedPath != target.encodedPath || source.encodedQuery != target.encodedQuery) {
    return null
  }
  return target.origin()
}

private fun canFollowWithoutPersisting(
    method: HttpMethod,
    source: Url,
    target: Url,
    status: HttpStatusCode,
): Boolean {
  if (source.origin() != target.origin()) return false
  if (method == HttpMethod.Get || method == HttpMethod.Head) return true
  return status == HttpStatusCode.TemporaryRedirect || status == HttpStatusCode.PermanentRedirect
}

internal fun Url.origin(): String {
  val renderedHost = if (host.contains(':')) "[$host]" else host
  val portSuffix =
      if (specifiedPort == 0 || specifiedPort == protocol.defaultPort) "" else ":$specifiedPort"
  return "${protocol.name}://$renderedHost$portSuffix"
}
