package ddd.kc.data.remote.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.remote.network.challenge.PawchiveCfSessionStore
import ddd.kc.data.remote.network.challenge.PawchiveChallengeResolver
import ddd.kc.fake.TestSecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

class PawchiveHttpGatewayChallengeTest {
  @Test
  fun challengeResolutionRetriesWithCfAndLoginCookies() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
      requests += request
      if (requests.size == 1) challengeResponse() else respond("ok", HttpStatusCode.OK)
    }
    val cfStore = PawchiveCfSessionStore(TestSecretStore(), Json)
    val loginStore = KcSessionStore(TestSecretStore()).apply { saveSession("login-token") }
    var resolutionCount = 0
    val resolver = PawchiveChallengeResolver { signal ->
      resolutionCount++
      cfStore.merge(signal.siteOrigin, "cf_clearance=solved", "Browser/1")
      true
    }
    val gateway =
        gateway(HttpClient(engine) { followRedirects = false }, cfStore, loginStore, resolver)

    assertEquals("ok", gateway.getText("/private", "test", authenticated = true))
    assertEquals(1, resolutionCount)
    assertEquals(2, requests.size)
    assertEquals(
        "cf_clearance=solved; session=login-token",
        requests.last().headers[HttpHeaders.Cookie],
    )
    assertEquals("Browser/1", requests.last().headers[HttpHeaders.UserAgent])
    assertEquals(
        "text/html,application/xhtml+xml",
        requests.last().headers[HttpHeaders.Accept],
    )
    assertEquals("en-US,en;q=0.9", requests.last().headers[HttpHeaders.AcceptLanguage])
  }

  @Test
  fun cancellationUsesDedicatedException() = runTest {
    val engine = MockEngine { challengeResponse() }
    val gateway =
        gateway(
            HttpClient(engine) { followRedirects = false },
            PawchiveCfSessionStore(TestSecretStore(), Json),
            KcSessionStore(TestSecretStore()),
            PawchiveChallengeResolver { false },
        )

    assertFailsWith<PawchiveChallengeCancelledException> { gateway.getText("/posts", "test") }
  }

  @Test
  fun retryThatRemainsChallengedUsesCfChallengeException() = runTest {
    val engine = MockEngine { challengeResponse() }
    val gateway =
        gateway(
            HttpClient(engine) { followRedirects = false },
            PawchiveCfSessionStore(TestSecretStore(), Json),
            KcSessionStore(TestSecretStore()),
            PawchiveChallengeResolver { true },
        )

    val error = assertFailsWith<PawchiveCfChallengeException> { gateway.getText("/posts", "test") }
    assertEquals("ray-test", error.cfRay)
  }

  private fun gateway(
      client: HttpClient,
      cfStore: PawchiveCfSessionStore,
      loginStore: KcSessionStore,
      resolver: PawchiveChallengeResolver,
  ): PawchiveHttpGateway = PawchiveHttpGateway(client, settings(), loginStore, cfStore, resolver)

  private fun settings(): AppSettings {
    val file = File.createTempFile("kc-cf-settings", ".preferences_pb").apply { delete() }
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath { file.absolutePath.toPath() },
        TestSecretStore(),
    )
  }

  private fun io.ktor.client.engine.mock.MockRequestHandleScope.challengeResponse() =
      respond(
          content =
              "<html><title>Just a moment...</title><div id=\"challenge-running\"></div></html>",
          status = HttpStatusCode.ServiceUnavailable,
          headers =
              headersOf(
                  HttpHeaders.Server to listOf("cloudflare"),
                  "cf-ray" to listOf("ray-test"),
                  HttpHeaders.ContentType to listOf("text/html"),
              ),
      )
}
