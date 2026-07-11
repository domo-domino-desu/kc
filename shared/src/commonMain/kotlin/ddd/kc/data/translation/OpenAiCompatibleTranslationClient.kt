package ddd.kc.data.translation

import ddd.kc.utils.renderBraceTemplate
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class OpenAiCompatibleTranslationClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val config =
        requireNotNull(request.openAiConfig) {
          "OpenAI compatible translation requires openAiConfig"
        }
    val apiKey = config.apiKey.trim()
    if (apiKey.isBlank()) throw IllegalArgumentException("OpenAI compatible apiKey is blank")

    val response =
        client.post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
          header(HttpHeaders.Authorization, "Bearer $apiKey")
          accept(ContentType.Application.Json)
          contentType(ContentType.Application.Json)
          setBody(
              buildChatCompletionsPayload(
                      model = config.model.trim().ifBlank { OpenAiTranslationConfig.defaultModel },
                      userPrompt =
                          resolvePromptTemplate(
                              template = config.promptTemplate,
                              input = request.sourceText,
                              targetLanguage = mapTargetDisplayName(request.targetLanguageCode),
                          ),
                  )
                  .toString()
          )
        }
    ensureTranslationSuccess(response.status.value, provider = "OpenAI compatible")

    val contentElement =
        json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
    val translated =
        when (contentElement) {
              is JsonPrimitive -> contentElement.contentOrNull
              is JsonArray ->
                  contentElement
                      .mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                      .joinToString("")
              else -> null
            }
            ?.trim()
            .orEmpty()

    return ensureTranslatedNonBlank(
        provider = "OpenAI compatible",
        translated = stripThinkTag(translated),
    )
  }

  private fun normalizeBaseUrl(baseUrl: String): String {
    val normalized = baseUrl.trim().trimEnd('/')
    return if (normalized.isBlank()) OpenAiTranslationConfig.defaultBaseUrl else normalized
  }

  private fun resolvePromptTemplate(
      template: String,
      input: String,
      targetLanguage: String,
  ): String =
      renderBraceTemplate(
          template = template.ifBlank { OpenAiTranslationConfig.defaultPromptTemplate },
          values = mapOf("TARGET_LANG" to targetLanguage, "SEPARATOR" to "%%", "INPUT" to input),
      )

  private fun stripThinkTag(text: String): String {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("<think>", ignoreCase = true)) return text
    val closeIndex = trimmed.indexOf("</think>", ignoreCase = true)
    if (closeIndex < 0) return text
    return trimmed.substring(closeIndex + "</think>".length).trim().ifBlank { text }
  }

  private fun mapTargetDisplayName(code: String): String =
      when (code.lowercase()) {
        "zh-cn" -> "简体中文"
        "zh-tw" -> "繁體中文"
        "ja" -> "日本語"
        "en" -> "English"
        else -> code
      }

  private fun buildChatCompletionsPayload(model: String, userPrompt: String) = buildJsonObject {
    put("model", JsonPrimitive(model))
    put(
        "messages",
        buildJsonArray {
          add(
              buildJsonObject {
                put("role", JsonPrimitive("system"))
                put(
                    "content",
                    JsonPrimitive(
                        "You are a professional translator. Return translated text only."
                    ),
                )
              }
          )
          add(
              buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(userPrompt))
              }
          )
        },
    )
    put("temperature", JsonPrimitive(0.2))
  }
}
