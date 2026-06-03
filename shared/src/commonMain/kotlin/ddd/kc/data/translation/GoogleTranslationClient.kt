package ddd.kc.data.translation

import ddd.kc.domain.translation.TranslationRequest
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class GoogleTranslationClient(
    private val transport: TranslationHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val response =
        transport.get(
            url = endpoint,
            request =
                TranslationHttpRequest(
                    parameters =
                        listOf(
                            "client" to "gtx",
                            "sl" to mapSourceLanguage(request.sourceLanguageCode),
                            "tl" to mapTargetLanguage(request.targetLanguageCode),
                            "dt" to "t",
                            "strip" to "1",
                            "nonced" to "1",
                            "q" to request.sourceText,
                        ),
                    accept = ContentType.Application.Json,
                ),
        )
    ensureTranslationSuccess(response.statusCode, response.body, provider = "Google")

    val root = json.parseToJsonElement(response.body).jsonArray
    val chunks = root.getOrNull(0)?.jsonArray.orEmpty()
    val translated =
        chunks
            .mapNotNull { it as? JsonArray }
            .mapNotNull { it.getOrNull(0)?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()

    return ensureTranslatedNonBlank(provider = "Google", translated = translated)
  }

  private fun mapSourceLanguage(code: String): String =
      if (code.equals("auto", ignoreCase = true)) "auto" else code

  private fun mapTargetLanguage(code: String): String =
      when (code.lowercase()) {
        "zh-cn" -> "zh-CN"
        "zh-tw" -> "zh-TW"
        else -> code
      }

  private companion object {
    private const val endpoint = "https://translate.googleapis.com/translate_a/single"
  }
}
