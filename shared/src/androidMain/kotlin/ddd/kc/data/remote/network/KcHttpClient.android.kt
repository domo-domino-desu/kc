package ddd.kc.data.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun buildKcHttpClient(
    followRedirects: Boolean,
    useDefaultUserAgent: Boolean,
): HttpClient =
    HttpClient(OkHttp) {
      this.followRedirects = followRedirects
      configureKcHttpClient(useDefaultUserAgent)
    }
