package ddd.kc.data.remote.network.challenge

import ddd.kc.data.remote.network.KcSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay

class PawchiveChallengeProbeVerifier(
    private val client: HttpClient,
    private val cfSessionStore: PawchiveCfSessionStore,
    private val loginSessionStore: KcSessionStore,
    private val retryDelaysMs: List<Long> = listOf(0L, 600L, 1_200L),
) {
  suspend fun verify(challenge: PawchiveChallengeSignal) {
    var lastFailure: PawchiveChallengeResponse? = null
    for (delayMs in retryDelaysMs) {
      if (delayMs > 0) delay(delayMs)
      val context = cfSessionStore.load(challenge.siteOrigin)
      val response =
          client.get(challenge.requestUrl) {
            headers {
              this[HttpHeaders.UserAgent] = context.userAgent
              this[HttpHeaders.Accept] = "text/html,application/xhtml+xml"
              this[HttpHeaders.AcceptLanguage] = "en-US,en;q=0.9"
              val cookies =
                  combineCookieHeaders(
                      context.cookieHeader,
                      loginSessionStore.getSession()?.let { "session=$it" }.orEmpty(),
                  )
              if (cookies.isNotBlank()) this[HttpHeaders.Cookie] = cookies
            }
          }
      if (
          PawchiveOriginPolicy.isTrustedRequest(
              response.call.request.url.toString(),
              challenge.siteOrigin,
          )
      ) {
        cfSessionStore.mergeSetCookieHeaders(
            challenge.siteOrigin,
            response.headers.getAll(HttpHeaders.SetCookie).orEmpty(),
        )
      }
      val status = response.status.value
      if (status !in setOf(403, 429, 503)) {
        return
      }
      val body = response.bodyAsText()
      val challengeResponse =
          PawchiveChallengeClassifier.classify(response.status.value, response.headers, body)
      if (challengeResponse == null) {
        return
      }
      lastFailure = challengeResponse
    }
    throw IllegalStateException(
        "Cloudflare challenge is still active" +
            lastFailure?.cfRay?.let { " (CF-Ray: $it)" }.orEmpty()
    )
  }
}
