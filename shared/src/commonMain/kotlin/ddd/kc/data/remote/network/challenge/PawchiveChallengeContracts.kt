package ddd.kc.data.remote.network.challenge

import kotlinx.coroutines.flow.StateFlow

data class PawchiveChallengeSignal(
    val requestUrl: String,
    val siteOrigin: String,
    val cfRay: String?,
)

fun interface PawchiveChallengeResolver {
  suspend fun awaitResolution(challenge: PawchiveChallengeSignal): Boolean
}

interface PawchiveChallengeController {
  val state: StateFlow<PawchiveChallengeUiState>

  suspend fun prepareWebViewSession(port: SessionWebViewPort, challenge: PawchiveChallengeSignal)

  suspend fun syncUserAgentFromWebView(port: SessionWebViewPort)

  suspend fun confirmFromWebView(port: SessionWebViewPort): Boolean

  suspend fun cancel()
}

interface SessionWebViewPort {
  val lastLoadedUrl: String?

  fun loadUrl(url: String)

  suspend fun captureCookieHeader(url: String): String

  suspend fun injectCookieHeader(url: String, cookieHeader: String)

  suspend fun readUserAgent(): String?
}

sealed interface PawchiveChallengeUiState {
  data object Idle : PawchiveChallengeUiState

  data class Active(
      val challenge: PawchiveChallengeSignal,
      val status: PawchiveChallengeStatus,
  ) : PawchiveChallengeUiState
}

sealed interface PawchiveChallengeStatus {
  data object AwaitingUserAction : PawchiveChallengeStatus

  data object Verifying : PawchiveChallengeStatus

  data class VerificationFailed(val detail: String?) : PawchiveChallengeStatus
}
