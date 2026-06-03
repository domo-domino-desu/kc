package ddd.kc.data.translation

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
