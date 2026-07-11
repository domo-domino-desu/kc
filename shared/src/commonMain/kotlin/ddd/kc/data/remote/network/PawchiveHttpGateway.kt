package ddd.kc.data.remote.network

import ddd.kc.data.local.settings.AppSettings
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

private val gatewayLog = KcLog.withTag("PawchiveHttpGateway")

class PawchiveHttpGateway(
    private val client: HttpClient,
    private val settings: AppSettings,
    private val sessionStore: KcSessionStore,
) {
  fun hasSession(): Boolean = sessionStore.hasSession()

  fun clearSession() = sessionStore.clearSession()

  suspend fun getText(
      path: String,
      label: String,
      authenticated: Boolean = false,
      block: HttpRequestBuilder.() -> Unit = {},
  ): String =
      requireSuccess(
          client.get(settings.baseUrl() + path) {
            if (authenticated) header(HttpHeaders.Cookie, sessionStore.cookieHeader())
            block()
          },
          label,
      )

  suspend fun postUnit(
      path: String,
      label: String,
      authenticated: Boolean,
      block: HttpRequestBuilder.() -> Unit = {},
  ) {
    requireSuccess(
        client.post(settings.baseUrl() + path) {
          if (authenticated) header(HttpHeaders.Cookie, sessionStore.cookieHeader())
          block()
        },
        label,
    )
  }

  suspend fun deleteUnit(path: String, label: String, authenticated: Boolean) {
    requireSuccess(
        client.delete(settings.baseUrl() + path) {
          if (authenticated) header(HttpHeaders.Cookie, sessionStore.cookieHeader())
        },
        label,
    )
  }

  private suspend fun requireSuccess(
      response: io.ktor.client.statement.HttpResponse,
      label: String,
  ): String {
    if (response.status == HttpStatusCode.Unauthorized) {
      gatewayLog.w { "$label -> 需要登录" }
      throw AuthRequiredException()
    }
    if (!response.status.isSuccess()) {
      val bodyLength = resultOfSuspend { response.bodyAsText() }.getOrDefault("").length
      gatewayLog.w { "$label -> 失败(status=${response.status.value},bodyLength=$bodyLength)" }
      throw PawchiveApiException(response.status.value, "HTTP ${response.status.value}")
    }
    return response.bodyAsText()
  }
}
