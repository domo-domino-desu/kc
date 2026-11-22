package ddd.kc.data.remote.translation

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
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

/**
 * Direct Kotlin port of Index-Translate's inference/llm/call_api.py text request. Uses the public
 * endpoint without an API key or the configurable OpenAI provider.
 */
internal class IndexTranslateTranslationClient(baseClient: HttpClient) : TranslationProviderClient {
  private val client =
      baseClient.config {
        install(HttpTimeout) {
          requestTimeoutMillis = 120_000
          socketTimeoutMillis = 120_000
        }
      }
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun translate(request: TranslationRequest): String {
    val payload = buildJsonObject {
      put("model", JsonPrimitive(request.indexTranslateModel.modelId))
      put(
          "messages",
          buildJsonArray {
            add(
                buildJsonObject {
                  put("role", JsonPrimitive("user"))
                  put("content", JsonPrimitive(buildIndexTranslatePrompt(request)))
                }
            )
          },
      )
      put("temperature", JsonPrimitive(0.0))
      // The script exposes max_tokens as a parameter. Keep its 1024-token minimum while
      // allowing existing multi-paragraph chunks enough output to preserve every block.
      put(
          "max_tokens",
          JsonPrimitive((request.sourceText.length.toLong() * 2).coerceIn(1024, 8192).toInt()),
      )
      put("stream", JsonPrimitive(false))
      put("chat_template_kwargs", buildJsonObject { put("enable_thinking", JsonPrimitive(false)) })
    }
    val response =
        client.post(endpoint) {
          header(HttpHeaders.UserAgent, "Index-Translate-Client/1.0")
          contentType(ContentType.Application.Json)
          setBody(payload.toString())
        }
    check(response.status.value in 200..299) {
      "IndexTranslate translation failed: status=${response.status.value}"
    }
    val choice =
        json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject ?: error("IndexTranslate translation result is missing")
    check(choice["finish_reason"]?.jsonPrimitive?.contentOrNull != "length") {
      "IndexTranslate translation reached the output limit"
    }
    val content =
        choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: error("IndexTranslate translation result is missing")
    // Same strip_think behavior as the reference: remove everything through a closing tag,
    // then remove a remaining opening tag, even if the closing tag was absent.
    val translated =
        content.substringAfter("</think>", content).trim().removePrefix("<think>").trim()
    return ensureTranslatedNonBlank("IndexTranslate", translated)
  }

  companion object {
    const val endpoint = "https://index-translate.bilibili.com/v1/chat/completions"
  }
}

internal fun buildIndexTranslatePrompt(request: TranslationRequest): String {
  val targetCode = normalizeIndexLanguage(request.targetLanguageCode)
  val target =
      indexLanguageNames[targetCode]
          ?: throw IllegalArgumentException("Unsupported IndexTranslate target language")
  val sourceCode = normalizeIndexLanguage(request.sourceLanguageCode)
  val source =
      if (sourceCode.isBlank() || sourceCode == "auto") ""
      else indexLanguageNames[sourceCode] ?: request.sourceLanguageCode
  val constraints = buildList {
    add("请严格保留段落分隔标记 %% 的数量和顺序。")
    when (request.targetLanguageCode.lowercase()) {
      "zh-cn",
      "zh-hans" -> add("译文使用简体中文。")
      "zh-tw",
      "zh-hant" -> add("译文使用繁体中文。")
    }
  }
  return buildString {
    append("请将以下${source}文本翻译成${target}，并且严格遵循所有约束要求。\n\n")
    append("【源文】\n").append(request.sourceText.trim()).append("\n\n")
    append("【约束要求】\n")
    append(constraints.mapIndexed { i, text -> "${i + 1}. 【注意】$text" }.joinToString("\n"))
    append("\n\n只输出译文，不要有任何额外说明。")
  }
}

private fun normalizeIndexLanguage(code: String): String =
    when (val normalized = code.trim().lowercase()) {
      "zh-cn",
      "zh-tw",
      "zh-hans",
      "zh-hant" -> "zh"
      "en-us",
      "en-gb" -> "en"
      else -> normalized
    }

private val indexLanguageNames =
    mapOf(
        "en" to "英语",
        "zh" to "中文",
        "de" to "德语",
        "fr" to "法语",
        "es" to "西班牙语",
        "ja" to "日语",
        "ko" to "韩语",
        "pt" to "葡萄牙语",
        "ru" to "俄语",
        "ar" to "阿拉伯语",
        "it" to "意大利语",
        "nl" to "荷兰语",
        "pl" to "波兰语",
        "ro" to "罗马尼亚语",
        "sv" to "瑞典语",
        "tr" to "土耳其语",
        "hi" to "印地语",
        "vi" to "越南语",
        "th" to "泰语",
        "id" to "印尼语",
        "ms" to "马来语",
        "fil" to "菲律宾语",
        "ukr_cyrl" to "乌克兰语",
        "fas_arab" to "波斯语",
        "ces_latn" to "捷克语",
        "ell_grek" to "希腊语",
        "dan_latn" to "丹麦语",
        "hun_latn" to "匈牙利语",
        "fin_latn" to "芬兰语",
        "nob_latn" to "书面挪威语",
        "slk_latn" to "斯洛伐克语",
        "bul_cyrl" to "保加利亚语",
    )
