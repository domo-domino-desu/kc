package ddd.kc.ui.state

data class TranslationBlockState(
    val originalHtml: String,
    val translated: String? = null,
    val status: TranslationStatus = TranslationStatus.IDLE,
)

enum class TranslationStatus {
  IDLE,
  PENDING,
  SUCCESS,
  EMPTY,
  FAILURE,
}

data class ContentTranslationState(
    val blocks: List<TranslationBlockState> = emptyList(),
    val isTranslating: Boolean = false,
    val showTranslation: Boolean = false,
)
