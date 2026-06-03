package ddd.kc.data.translation

import ddd.kc.domain.translation.TranslationRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class MicrosoftTranslationClient(
    private val transport: TranslationHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val tokenResponse = transport.get(tokenEndpoint)
    ensureTranslationSuccess(
        tokenResponse.statusCode,
        tokenResponse.body,
        provider = "Microsoft token",
    )
    val token = tokenResponse.body.trim()
    if (token.isBlank()) throw IllegalStateException("Microsoft token is blank")

    val parameters = buildList {
      add("api-version" to "3.0")
      add("to" to mapTargetLanguage(request.targetLanguageCode))
      add("includeSentenceLength" to "true")
      add("textType" to "html")
      val source = request.sourceLanguageCode.trim()
      if (!source.equals("auto", ignoreCase = true) && source.isNotBlank()) {
        add("from" to source)
      }
    }

    val response =
        transport.post(
            url = translateEndpoint,
            request =
                TranslationHttpRequest(
                    parameters = parameters,
                    headers = listOf(HttpHeaders.Authorization to "Bearer $token"),
                    accept = ContentType.Application.Json,
                    contentType = ContentType.Application.Json,
                    body =
                        buildJsonArray {
                              add(
                                  buildJsonObject { put("Text", JsonPrimitive(request.sourceText)) }
                              )
                            }
                            .toString(),
                ),
        )
    ensureTranslationSuccess(response.statusCode, response.body, provider = "Microsoft")

    val translated =
        json
            .parseToJsonElement(response.body)
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
