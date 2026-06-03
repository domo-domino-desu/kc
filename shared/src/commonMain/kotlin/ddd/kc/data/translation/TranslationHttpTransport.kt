package ddd.kc.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal data class TranslationHttpRequest(
    val parameters: List<Pair<String, String>> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList(),
    val accept: ContentType? = null,
    val contentType: ContentType? = null,
    val body: String? = null,
)

internal data class TranslationHttpResponse(val statusCode: Int, val body: String)

internal interface TranslationHttpTransport {
  suspend fun get(
      url: String,
      request: TranslationHttpRequest = TranslationHttpRequest(),
  ): TranslationHttpResponse

  suspend fun post(url: String, request: TranslationHttpRequest): TranslationHttpResponse
}

internal class KtorTranslationHttpTransport(private val client: HttpClient) :
    TranslationHttpTransport {
  override suspend fun get(url: String, request: TranslationHttpRequest): TranslationHttpResponse {
    val response = client.get(url) { applyRequest(request) }
    return TranslationHttpResponse(statusCode = response.status.value, body = response.bodyAsText())
  }

  override suspend fun post(url: String, request: TranslationHttpRequest): TranslationHttpResponse {
    val response =
        client.post(url) {
          applyRequest(request)
          request.body?.let(::setBody)
        }
    return TranslationHttpResponse(statusCode = response.status.value, body = response.bodyAsText())
  }

  private fun io.ktor.client.request.HttpRequestBuilder.applyRequest(
      request: TranslationHttpRequest
  ) {
    request.parameters.forEach { (key, value) -> parameter(key, value) }
    request.headers.forEach { (key, value) -> header(key, value) }
    request.accept?.let(::accept)
    request.contentType?.let(::contentType)
  }
}
