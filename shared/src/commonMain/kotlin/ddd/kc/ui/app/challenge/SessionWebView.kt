package ddd.kc.ui.app.challenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ddd.kc.data.remote.network.challenge.SessionWebViewPort
import dev.nucleusframework.webview.cookie.Cookie
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val USER_AGENT_TIMEOUT_MS = 1_500L

data class SessionWebViewAdapter(
    val port: SessionWebViewPort,
    val state: WebViewState,
    val navigator: WebViewNavigator,
)

@Composable
fun rememberSessionWebViewAdapter(initialUrl: String): SessionWebViewAdapter {
  val state = rememberWebViewState(initialUrl)
  val navigator = rememberWebViewNavigator()
  return remember(state, navigator) {
    SessionWebViewAdapter(
        port = ComposeSessionWebViewPort(state, navigator),
        state = state,
        navigator = navigator,
    )
  }
}

@Composable
fun SessionWebView(adapter: SessionWebViewAdapter, modifier: Modifier) {
  WebView(state = adapter.state, navigator = adapter.navigator, modifier = modifier)
}

private class ComposeSessionWebViewPort(
    private val state: WebViewState,
    private val navigator: WebViewNavigator,
) : SessionWebViewPort {
  override val lastLoadedUrl: String?
    get() = state.lastLoadedUrl

  override fun loadUrl(url: String) {
    navigator.loadUrl(url)
  }

  override suspend fun captureCookieHeader(url: String): String =
      state.cookieManager.getCookies(url).joinToString("; ") { "${it.name}=${it.value}" }

  override suspend fun injectCookieHeader(url: String, cookieHeader: String) {
    parseCookieHeader(cookieHeader).forEach { (name, value) ->
      state.cookieManager.setCookie(
          url = url,
          cookie =
              Cookie(
                  name = name,
                  value = value,
                  domain = null,
                  path = "/",
                  isSecure = false,
                  isHttpOnly = false,
              ),
      )
    }
  }

  override suspend fun readUserAgent(): String? =
      withTimeoutOrNull(USER_AGENT_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
          navigator.evaluateJavaScript("navigator.userAgent") { raw ->
            if (continuation.isActive) {
              continuation.resume(raw.removeSurrounding("\"").trim())
            }
          }
        }
      }
}

private fun parseCookieHeader(raw: String): List<Pair<String, String>> =
    raw.split(';').mapNotNull { token ->
      val pair = token.trim()
      if (!pair.contains('=')) return@mapNotNull null
      val name = pair.substringBefore('=').trim()
      name.takeIf(String::isNotBlank)?.let { it to pair.substringAfter('=', "").trim() }
    }
