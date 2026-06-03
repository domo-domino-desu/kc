package ddd.kc.data.translation

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
