package ddd.kc.data.remote.translation

import ddd.kc.data.model.OpenAiTranslationConfig
import ddd.kc.data.model.TranslationProvider

data class TranslationBlock(val originalHtml: String, val sourceText: String)

sealed interface TranslationBlockResult {
  data class Success(val translatedText: String) : TranslationBlockResult

  data object EmptyResult : TranslationBlockResult

  data class Failure(val cause: Throwable) : TranslationBlockResult
}

internal data class TranslationChunk(val startIndex: Int, val sourceTexts: List<String>)

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
