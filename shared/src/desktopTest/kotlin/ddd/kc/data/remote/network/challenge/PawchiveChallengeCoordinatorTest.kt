package ddd.kc.data.remote.network.challenge

import ddd.kc.data.remote.network.KcSessionStore
import ddd.kc.fake.TestSecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class PawchiveChallengeCoordinatorTest {
  @Test
  fun confirmationPersistsCfCookiesAndUserAgentThenWakesWaitingRequests() = runTest {
    val fixture = fixture(probeStillChallenged = false)
    fixture.loginStore.saveSession("login-secret")
    val awaiting =
        async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.awaitResolution(signal()) }
    val port = FakeWebViewPort("cf_clearance=solved; session=webview", "WebView/1")

    fixture.coordinator.prepareWebViewSession(port, signal())
    assertTrue(port.injectedCookies.any { "session=login-secret" in it })
    assertTrue(fixture.coordinator.confirmFromWebView(port))
    assertTrue(awaiting.await())
    val persisted = fixture.cfStore.load(SITE)
    assertTrue("cf_clearance=solved" in persisted.cookieHeader)
    assertEquals("WebView/1", persisted.userAgent)
    assertTrue("__cf_bm=probe" in persisted.cookieHeader)
    assertEquals(
        "text/html,application/xhtml+xml",
        fixture.probeRequests.single().headers[HttpHeaders.Accept],
    )
    assertEquals(
        "en-US,en;q=0.9",
        fixture.probeRequests.single().headers[HttpHeaders.AcceptLanguage],
    )
    assertEquals(PawchiveChallengeUiState.Idle, fixture.sessionStorage.state.value)
  }

  @Test
  fun failedProbeRollsBackSessionAndKeepsOverlayActive() = runTest {
    val fixture = fixture(probeStillChallenged = true)
    fixture.cfStore.save(SITE, PawchiveCfSession("cf_clearance=old", "Old/1"))
    val awaiting =
        async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.awaitResolution(signal()) }

    assertFalse(
        fixture.coordinator.confirmFromWebView(FakeWebViewPort("cf_clearance=new", "WebView/2"))
    )
    assertEquals(PawchiveCfSession("cf_clearance=old", "Old/1"), fixture.cfStore.load(SITE))
    assertTrue(fixture.sessionStorage.state.value is PawchiveChallengeUiState.Active)
    fixture.coordinator.cancel()
    assertFalse(awaiting.await())
  }

  private fun fixture(probeStillChallenged: Boolean): Fixture {
    val cfStore = PawchiveCfSessionStore(TestSecretStore(), Json)
    val loginStore = KcSessionStore(TestSecretStore())
    val probeRequests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
      probeRequests += request
      if (probeStillChallenged) {
        respond(
            content = "<title>Just a moment...</title><div id=\"challenge-running\"></div>",
            status = HttpStatusCode.ServiceUnavailable,
            headers =
                headersOf(
                    HttpHeaders.Server to listOf("cloudflare"),
                    "cf-ray" to listOf("probe-ray"),
                ),
        )
      } else {
        respond(
            content = "ok",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.SetCookie, "__cf_bm=probe; Path=/; Secure"),
        )
      }
    }
    val storage = PawchiveChallengeSessionStorage()
    val coordinator =
        PawchiveChallengeCoordinator(
            sessionStorage = storage,
            cfSessionStore = cfStore,
            loginSessionStore = loginStore,
            probeVerifier =
                PawchiveChallengeProbeVerifier(
                    client = HttpClient(engine),
                    cfSessionStore = cfStore,
                    loginSessionStore = loginStore,
                    retryDelaysMs = listOf(0L),
                ),
        )
    return Fixture(coordinator, cfStore, loginStore, storage, probeRequests)
  }

  private fun signal() = PawchiveChallengeSignal("$SITE/posts", SITE, "ray")

  private data class Fixture(
      val coordinator: PawchiveChallengeCoordinator,
      val cfStore: PawchiveCfSessionStore,
      val loginStore: KcSessionStore,
      val sessionStorage: PawchiveChallengeSessionStorage,
      val probeRequests: List<HttpRequestData>,
  )

  private class FakeWebViewPort(
      private val cookies: String,
      private val userAgent: String,
  ) : SessionWebViewPort {
    val injectedCookies = mutableListOf<String>()
    override val lastLoadedUrl: String = "$SITE/posts"

    override fun loadUrl(url: String) = Unit

    override suspend fun captureCookieHeader(url: String): String = cookies

    override suspend fun injectCookieHeader(url: String, cookieHeader: String) {
      injectedCookies += cookieHeader
    }

    override suspend fun readUserAgent(): String = userAgent
  }

  companion object {
    private const val SITE = "https://pawchive.st"
  }
}
