package ddd.kc.data.remote.network

import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.remote.network.challenge.PawchiveCfSessionStore
import ddd.kc.data.remote.network.challenge.PawchiveChallengeClassifier
import ddd.kc.data.remote.network.challenge.PawchiveChallengeResolver
import ddd.kc.data.remote.network.challenge.PawchiveChallengeSignal
import ddd.kc.data.remote.network.challenge.PawchiveOriginPolicy
import ddd.kc.data.remote.network.challenge.combineCookieHeaders
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_REDIRECTS = 5
private val gatewayLog = KcLog.withTag("PawchiveHttpGateway")

class PawchiveHttpGateway(
    private val client: HttpClient,
    private val settings: AppSettings,
    private val sessionStore: KcSessionStore,
    private val cfSessionStore: PawchiveCfSessionStore,
    private val challengeResolver: PawchiveChallengeResolver,
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
    var challengeRetryCount = 0

    while (true) {
      val requestUrl = directRedirectUrl ?: Url(baseUrl + path)
      val siteOrigin = requireNotNull(PawchiveOriginPolicy.normalizedSiteOrigin(baseUrl))
      val cfContext =
          if (PawchiveOriginPolicy.isTrustedRequest(requestUrl.toString(), siteOrigin)) {
            cfSessionStore.load(siteOrigin)
          } else {
            null
          }
      val response =
          client.request {
            this.method = method
            url(requestUrl)
            headers {
              cfContext?.let { this[HttpHeaders.UserAgent] = it.userAgent }
              this[HttpHeaders.Accept] = "text/html,application/xhtml+xml"
              this[HttpHeaders.AcceptLanguage] = "en-US,en;q=0.9"
              val cookies =
                  combineCookieHeaders(
                      cfContext?.cookieHeader.orEmpty(),
                      if (authenticated && cfContext != null) sessionStore.cookieHeader() else "",
                  )
              if (cookies.isNotBlank()) this[HttpHeaders.Cookie] = cookies
            }
            block()
          }
      val finalRequestUrl = response.call.request.url
      if (!visitedUrls.add(finalRequestUrl.toString())) {
        discardBody(response)
        throw redirectFailure(label, response.status, "检测到重定向循环")
      }

      if (PawchiveOriginPolicy.isTrustedRequest(finalRequestUrl.toString(), siteOrigin)) {
        cfSessionStore.mergeSetCookieHeaders(
            siteOrigin,
            response.headers.getAll(HttpHeaders.SetCookie).orEmpty(),
        )
      }

      if (!response.status.isRedirectStatus()) {
        val body = response.bodyAsText()
        val challenge =
            PawchiveChallengeClassifier.classify(response.status.value, response.headers, body)
        if (challenge != null) {
          if (challengeRetryCount >= 1) {
            throw PawchiveCfChallengeException(challenge.cfRay)
          }
          val resolved =
              challengeResolver.awaitResolution(
                  PawchiveChallengeSignal(
                      requestUrl = finalRequestUrl.toString(),
                      siteOrigin = siteOrigin,
                      cfRay = challenge.cfRay,
                  )
              )
          if (!resolved) throw PawchiveChallengeCancelledException()
          challengeRetryCount++
          visitedUrls.remove(finalRequestUrl.toString())
          directRedirectUrl = finalRequestUrl
          continue
        }
        return requireSuccess(response, label, body)
      }
      if (++redirectCount > MAX_REDIRECTS) {
        discardBody(response)
        throw redirectFailure(label, response.status, "重定向次数超过上限")
      }

      val target = parseRedirectTarget(response)
      if (target == null) {
        discardBody(response)
        throw redirectFailure(label, response.status, "Location 不是有效的 HTTPS URL")
      }

      val canonicalOrigin = canonicalRedirectOrigin(finalRequestUrl, target, response.status)
      if (canonicalOrigin != null) {
        discardBody(response)
        baseUrl = adoptCanonicalOrigin(finalRequestUrl.origin(), canonicalOrigin)
        directRedirectUrl = Url(baseUrl + finalRequestUrl.encodedPathAndQuery)
        continue
      }

      if (!canFollowWithoutPersisting(method, finalRequestUrl, target, response.status)) {
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

  private fun requireSuccess(response: HttpResponse, label: String, body: String): String {
    if (response.status == HttpStatusCode.Unauthorized) {
      gatewayLog.w { "$label -> 需要登录" }
      throw AuthRequiredException()
    }
    if (!response.status.isSuccess()) {
      val bodyLength = body.length
      gatewayLog.w { "$label -> 失败(status=${response.status.value},bodyLength=$bodyLength)" }
      throw PawchiveApiException(response.status.value, "HTTP ${response.status.value}")
    }
    return body
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
