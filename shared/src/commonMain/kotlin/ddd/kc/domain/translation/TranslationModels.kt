package ddd.kc.domain.translation

data class TranslationBlock(val originalHtml: String, val sourceText: String)

sealed interface TranslationBlockResult {
  data class Success(val translatedText: String) : TranslationBlockResult

  data object EmptyResult : TranslationBlockResult

  data class Failure(val cause: Throwable) : TranslationBlockResult
}

data class TranslationChunk(val startIndex: Int, val sourceTexts: List<String>)
