package ddd.kc.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

expect fun buildKcHttpClient(cookieStorage: AcceptAllCookiesStorage): HttpClient

internal fun HttpClientConfig<*>.configureKcHttpClient(cookieStorage: AcceptAllCookiesStorage) {
  install(HttpCookies) { storage = cookieStorage }
  defaultRequest { headers[HttpHeaders.UserAgent] = "Mozilla/5.0 (compatible; KC/1.0)" }
}
