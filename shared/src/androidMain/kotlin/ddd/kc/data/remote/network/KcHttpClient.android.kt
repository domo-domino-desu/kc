package ddd.kc.data.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage

actual fun buildKcHttpClient(
    cookieStorage: AcceptAllCookiesStorage,
    followRedirects: Boolean,
): HttpClient =
    HttpClient(OkHttp) {
      this.followRedirects = followRedirects
      configureKcHttpClient(cookieStorage)
    }
