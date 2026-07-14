package ddd.kc.data.remote.network.challenge

import ddd.kc.data.remote.network.KcSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

class PawchiveChallengeCoordinator(
    private val sessionStorage: PawchiveChallengeSessionStorage,
    private val cfSessionStore: PawchiveCfSessionStore,
    private val loginSessionStore: KcSessionStore,
    private val probeVerifier: PawchiveChallengeProbeVerifier,
) : PawchiveChallengeResolver, PawchiveChallengeController {
  override val state: StateFlow<PawchiveChallengeUiState> = sessionStorage.state

  override suspend fun awaitResolution(challenge: PawchiveChallengeSignal): Boolean {
    val acquisition = sessionStorage.acquire(challenge)
    return acquisition.session.deferred.await()
  }

  override suspend fun prepareWebViewSession(
      port: SessionWebViewPort,
      challenge: PawchiveChallengeSignal,
  ) {
    val context = cfSessionStore.load(challenge.siteOrigin)
    val cookies =
        combineCookieHeaders(
            context.cookieHeader,
            loginSessionStore.getSession()?.let { "session=$it" }.orEmpty(),
        )
    challengeUrls(challenge, port.lastLoadedUrl).forEach { url ->
      port.injectCookieHeader(url, cookies)
    }
  }

  override suspend fun syncUserAgentFromWebView(port: SessionWebViewPort) {
    val session = sessionStorage.current() ?: return
    val userAgent = readUserAgentWithRetry(port)
    if (userAgent.isNotBlank()) {
      cfSessionStore.merge(
          session.challenge.siteOrigin,
          rawCookieHeader = "",
          userAgent = userAgent,
      )
    }
  }

  override suspend fun confirmFromWebView(port: SessionWebViewPort): Boolean {
    val session = sessionStorage.current() ?: return false
    sessionStorage.markVerifying(session)
    val challenge = session.challenge
    val captured = captureCloudflareCookies(port, challengeUrls(challenge, port.lastLoadedUrl))
    if (captured.isBlank()) {
      sessionStorage.markVerificationFailed(session, "未抓取到 Cloudflare Cookie")
      return false
    }
    val snapshot = cfSessionStore.load(challenge.siteOrigin)
    return try {
      val userAgent = readUserAgentWithRetry(port)
      cfSessionStore.merge(
          siteOrigin = challenge.siteOrigin,
          rawCookieHeader = captured,
          userAgent = userAgent.takeIf(String::isNotBlank),
      )
      probeVerifier.verify(challenge)
      sessionStorage.complete(result = true)
      true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      cfSessionStore.save(challenge.siteOrigin, snapshot)
      sessionStorage.markVerificationFailed(session, error.message)
      false
    }
  }

  override suspend fun cancel() {
    sessionStorage.complete(result = false)
  }

  private fun challengeUrls(
      challenge: PawchiveChallengeSignal,
      lastLoadedUrl: String?,
  ): List<String> =
      listOfNotNull(
              challenge.siteOrigin,
              PawchiveOriginPolicy.mediaOrigin(challenge.siteOrigin),
              challenge.requestUrl,
              lastLoadedUrl,
          )
          .map(String::trim)
          .filter(String::isNotBlank)
          .distinct()
}

private suspend fun captureCloudflareCookies(
    port: SessionWebViewPort,
    urls: List<String>,
): String {
  repeat(8) { attempt ->
    val merged = linkedMapOf<String, String>()
    urls.forEach { url ->
      parseCookieHeader(port.captureCookieHeader(url)).forEach { (name, value) ->
        if (isCloudflareCookieName(name)) merged[name] = value
      }
    }
    if (merged.isNotEmpty()) {
      return merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }
    if (attempt < 7) delay(450L)
  }
  return ""
}

private suspend fun readUserAgentWithRetry(port: SessionWebViewPort): String {
  repeat(3) { attempt ->
    port.readUserAgent()?.trim()?.takeIf(String::isNotBlank)?.let {
      return it
    }
    if (attempt < 2) delay(220L)
  }
  return ""
}
