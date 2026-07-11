package ddd.kc.data.remote.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class MicrosoftTranslationClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val tokenResponse = client.get(tokenEndpoint)
    ensureTranslationSuccess(tokenResponse.status.value, provider = "Microsoft token")
    val token = tokenResponse.bodyAsText().trim()
    if (token.isBlank()) throw IllegalStateException("Microsoft token is blank")

    val response =
        client.post(translateEndpoint) {
          parameter("api-version", "3.0")
          parameter("to", mapTargetLanguage(request.targetLanguageCode))
          parameter("includeSentenceLength", "true")
          parameter("textType", "html")
          val source = request.sourceLanguageCode.trim()
          if (!source.equals("auto", ignoreCase = true) && source.isNotBlank()) {
            parameter("from", source)
          }
          header(HttpHeaders.Authorization, "Bearer $token")
          accept(ContentType.Application.Json)
          contentType(ContentType.Application.Json)
          setBody(
              buildJsonArray {
                    add(buildJsonObject { put("Text", JsonPrimitive(request.sourceText)) })
                  }
                  .toString()
          )
        }
    ensureTranslationSuccess(response.status.value, provider = "Microsoft")

    val translated =
        json
            .parseToJsonElement(response.bodyAsText())
            .jsonArray
            .firstOrNull()
            ?.jsonObject
            ?.get("translations")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()

    return ensureTranslatedNonBlank(provider = "Microsoft", translated = translated)
  }

  private fun mapTargetLanguage(code: String): String =
      when (code.lowercase()) {
        "zh-cn" -> "zh-Hans"
        "zh-tw" -> "zh-Hant"
        else -> code
      }

  private companion object {
    private const val tokenEndpoint = "https://edge.microsoft.com/translate/auth"
    private const val translateEndpoint =
        "https://api-edge.cognitive.microsofttranslator.com/translate"
  }
}
