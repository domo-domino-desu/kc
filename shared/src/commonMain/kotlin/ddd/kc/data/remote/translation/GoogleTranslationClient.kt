package ddd.kc.data.remote.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class GoogleTranslationClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val response =
        client.get(endpoint) {
          parameter("client", "gtx")
          parameter("sl", mapSourceLanguage(request.sourceLanguageCode))
          parameter("tl", mapTargetLanguage(request.targetLanguageCode))
          parameter("dt", "t")
          parameter("strip", "1")
          parameter("nonced", "1")
          parameter("q", request.sourceText)
          accept(ContentType.Application.Json)
        }
    ensureTranslationSuccess(response.status.value, provider = "Google")

    val root = json.parseToJsonElement(response.bodyAsText()).jsonArray
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
