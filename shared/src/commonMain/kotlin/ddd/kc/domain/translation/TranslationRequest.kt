package ddd.kc.domain.translation

import ddd.kc.data.translation.OpenAiTranslationConfig
import ddd.kc.data.translation.TranslationProvider

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

interface TranslationPort {
  suspend fun translate(request: TranslationRequest): String
}
