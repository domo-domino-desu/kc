package ddd.kc.data.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders

private val httpLog = Logger.withTag("KcHttp")

expect fun buildKcHttpClient(cookieStorage: AcceptAllCookiesStorage): HttpClient

internal fun HttpClientConfig<*>.configureKcHttpClient(cookieStorage: AcceptAllCookiesStorage) {
  install(HttpCookies) { storage = cookieStorage }
  install(Logging) {
    logger =
        object : io.ktor.client.plugins.logging.Logger {
          override fun log(message: String) = httpLog.d { message }
        }
    level = LogLevel.INFO
  }
  defaultRequest {
    headers[HttpHeaders.UserAgent] = "Mozilla/5.0 (compatible; KC/1.0)"
    headers[HttpHeaders.Accept] = "text/css"
  }
}
