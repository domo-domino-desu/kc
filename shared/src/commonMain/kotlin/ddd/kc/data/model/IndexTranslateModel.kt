package ddd.kc.data.model

enum class IndexTranslateModel(val modelId: String, val label: String) {
  TWO_B("Index-Translate-2B", "2B"),
  NINE_B("Index-Translate-9B", "9B"),
  THIRTY_FIVE_B("Index-Translate-35B-A3B", "35B");

  companion object {
    val default: IndexTranslateModel = NINE_B

    fun fromPersistedValue(raw: String?): IndexTranslateModel =
        entries.firstOrNull { it.modelId == raw?.trim() } ?: default
  }
}
