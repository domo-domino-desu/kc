package ddd.kc.data.remote.network.challenge

import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PawchiveChallengeClassifierTest {
  @Test
  fun recognizesCloudflareChallengeOnlyWhenMarkerAndCloudflareSignalArePresent() {
    val result =
        PawchiveChallengeClassifier.classify(
            statusCode = 403,
            headers =
                headersOf(
                    HttpHeaders.Server to listOf("cloudflare"),
                    "cf-ray" to listOf("ray-123"),
                ),
            body =
                "<html><title>Just a moment...</title><div id=\"challenge-running\"></div></html>",
        )

    assertEquals("ray-123", result?.cfRay)
  }

  @Test
  fun doesNotMisclassifyOrdinaryHttpFailures() {
    assertNull(
        PawchiveChallengeClassifier.classify(
            statusCode = 429,
            headers = headersOf(HttpHeaders.Server, "cloudflare"),
            body = "Too many requests",
        )
    )
    assertNull(
        PawchiveChallengeClassifier.classify(
            statusCode = 503,
            headers = headersOf(HttpHeaders.Server, "nginx"),
            body = "<title>Just a moment...</title>",
        )
    )
  }
}
