package ddd.kc.data.translation

data class TranslationBlock(val originalHtml: String, val sourceText: String)

sealed interface TranslationBlockResult {
  data class Success(val translatedText: String) : TranslationBlockResult

  data object EmptyResult : TranslationBlockResult

  data class Failure(val cause: Throwable) : TranslationBlockResult
}

internal data class TranslationChunk(val startIndex: Int, val sourceTexts: List<String>)

enum class TranslationProvider(val persistedValue: String) {
  GOOGLE("google"),
  MICROSOFT("microsoft"),
  OPENAI_COMPATIBLE("openai_compatible");

  companion object {
    val default: TranslationProvider = GOOGLE

    fun fromPersistedValue(raw: String?): TranslationProvider =
        entries.firstOrNull { it.persistedValue == raw?.trim() } ?: default
  }
}

enum class TranslationTargetLanguage(val persistedValue: String, val languageCode: String) {
  ZH_CN("zh-CN", "zh-CN"),
  EN("en", "en");

  companion object {
    val default: TranslationTargetLanguage = ZH_CN

    fun fromPersistedValue(raw: String?): TranslationTargetLanguage =
        entries.firstOrNull { it.persistedValue == raw?.trim() } ?: default
  }
}

data class OpenAiTranslationConfig(
    val baseUrl: String = defaultBaseUrl,
    val apiKey: String = "",
    val model: String = defaultModel,
    val promptTemplate: String = defaultPromptTemplate,
) {
  companion object {
    const val defaultBaseUrl: String = "https://api.openai.com/v1"
    const val defaultModel: String = "gpt-4o-mini"
    val defaultPromptTemplate: String =
        """
        你是专业翻译助手。请把输入文本翻译为 {TARGET_LANG}。
        请严格保留段落分隔标记 {SEPARATOR} 的数量和顺序。
        只输出译文，不要输出解释或额外内容。

        {INPUT}
        """
            .trimIndent()

    fun normalize(raw: OpenAiTranslationConfig): OpenAiTranslationConfig {
      val normalizedBaseUrl = raw.baseUrl.trim().trimEnd('/')
      return raw.copy(
          baseUrl = if (normalizedBaseUrl.isBlank()) defaultBaseUrl else normalizedBaseUrl,
          apiKey = raw.apiKey.trim(),
          model = raw.model.trim().ifBlank { defaultModel },
          promptTemplate = raw.promptTemplate.ifBlank { defaultPromptTemplate },
      )
    }
  }
}

data class TranslationSettings(
    val enabled: Boolean = true,
    val provider: TranslationProvider = TranslationProvider.default,
    val targetLanguageCode: String = "zh-CN",
    val chunkWordLimit: Int = 1024,
    val maxConcurrency: Int = 3,
    val openAiConfig: OpenAiTranslationConfig = OpenAiTranslationConfig(),
)

data class TranslationRequest(
    val provider: TranslationProvider,
    val sourceText: String,
    val sourceLanguageCode: String = "auto",
    val targetLanguageCode: String = "zh-CN",
    val openAiConfig: OpenAiTranslationConfig? = null,
) {
  fun normalized(): TranslationRequest =
      copy(
          sourceLanguageCode = sourceLanguageCode.trim(),
          targetLanguageCode = targetLanguageCode.trim(),
      )
}
