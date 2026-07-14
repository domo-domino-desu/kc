package ddd.kc.data.remote.network.challenge

import io.ktor.http.Headers

data class PawchiveChallengeResponse(val cfRay: String?)

object PawchiveChallengeClassifier {
  private val challengeStatuses = setOf(403, 429, 503)
  private val challengeMarkers =
      listOf(
          "<title>just a moment",
          "<title>attention required",
          "<title>checking your browser",
          "id=\"challenge-running\"",
          "id=\"cf-challenge-running\"",
          "id=\"challenge-form\"",
          "name=\"cf-turnstile-response\"",
          "name=\"h-captcha-response\"",
          "__cf_chl_managed_tk__",
          "cf_chl_opt",
      )

  fun classify(statusCode: Int, headers: Headers, body: String): PawchiveChallengeResponse? {
    if (statusCode !in challengeStatuses) return null
    val normalizedBody = body.lowercase()
    if (challengeMarkers.none(normalizedBody::contains)) return null
    val server = headers.entries().firstHeader("server").orEmpty()
    val cfRay = headers.entries().firstHeader("cf-ray")
    if (!server.contains("cloudflare", ignoreCase = true) && cfRay.isNullOrBlank()) return null
    return PawchiveChallengeResponse(cfRay = cfRay)
  }

  private fun Set<Map.Entry<String, List<String>>>.firstHeader(name: String): String? =
      firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
          ?.value
          ?.firstOrNull()
          ?.takeIf(String::isNotBlank)
}
