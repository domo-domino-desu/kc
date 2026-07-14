package ddd.kc.data.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

expect fun buildKcHttpClient(
    followRedirects: Boolean = true,
    useDefaultUserAgent: Boolean = true,
): HttpClient

internal fun HttpClientConfig<*>.configureKcHttpClient(useDefaultUserAgent: Boolean) {
  if (useDefaultUserAgent) {
    defaultRequest { headers[HttpHeaders.UserAgent] = "Mozilla/5.0 (compatible; KC/1.0)" }
  }
}
