package ddd.kc.data.remote.media

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.remote.network.challenge.PawchiveCfSession
import ddd.kc.data.remote.network.challenge.PawchiveCfSessionStore
import ddd.kc.data.remote.network.challenge.PawchiveChallengeResolver
import ddd.kc.fake.TestSecretStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

class CachedImageHttpClientChallengeTest {
  @Test
  fun trustedMediaReceivesCfContextWhileExternalMediaDoesNot() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
      requests += request
      respond(byteArrayOf(1), HttpStatusCode.OK)
    }
    val store = PawchiveCfSessionStore(TestSecretStore(), Json)
    store.save(SITE, PawchiveCfSession("cf_clearance=token", "Browser/1"))
    val client = client(engine, store)

    client.get("https://img.pawchive.st/data/a").bodyAsBytes()
    client.get("https://external.example/data/a").bodyAsBytes()

    assertEquals("cf_clearance=token", requests[0].headers[HttpHeaders.Cookie])
    assertEquals("Browser/1", requests[0].headers[HttpHeaders.UserAgent])
    assertEquals("en-US,en;q=0.9", requests[0].headers[HttpHeaders.AcceptLanguage])
    assertEquals("https://pawchive.st/", requests[0].headers[HttpHeaders.Referrer])
    assertNull(requests[1].headers[HttpHeaders.Cookie])
    assertNull(requests[1].headers[HttpHeaders.Referrer])
  }

  @Test
  fun mediaChallengeWaitsForResolutionAndRetriesOnce() = runTest {
    var requestCount = 0
    var resolutionCount = 0
    val engine = MockEngine {
      requestCount++
      if (requestCount == 1) {
        respond(
            content = "<title>Just a moment...</title><div id=\"challenge-running\"></div>",
            status = HttpStatusCode.ServiceUnavailable,
            headers =
                headersOf(
                    HttpHeaders.Server to listOf("cloudflare"),
                    "cf-ray" to listOf("media-ray"),
                    HttpHeaders.ContentType to listOf(ContentType.Text.Html.toString()),
                ),
        )
      } else {
        respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
      }
    }
    val store = PawchiveCfSessionStore(TestSecretStore(), Json)
    val client =
        createCachedImageHttpClient(
            engine,
            ImageProgressTracker(),
            settings(),
            store,
            PawchiveChallengeResolver {
              resolutionCount++
              true
            },
        )

    assertContentEquals(
        byteArrayOf(1, 2, 3),
        client.get("https://img.pawchive.st/data/a").bodyAsBytes(),
    )
    assertEquals(2, requestCount)
    assertEquals(1, resolutionCount)
  }

  private fun client(engine: MockEngine, store: PawchiveCfSessionStore) =
      createCachedImageHttpClient(
          engine,
          ImageProgressTracker(),
          settings(),
          store,
          PawchiveChallengeResolver { true },
      )

  private fun settings(): AppSettings {
    val file = File.createTempFile("kc-media-settings", ".preferences_pb").apply { delete() }
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath { file.absolutePath.toPath() },
        TestSecretStore(),
    )
  }

  companion object {
    private const val SITE = "https://pawchive.st"
  }
}
