package ddd.kc.data.translation

enum class TranslationTargetLanguage(val persistedValue: String, val languageCode: String) {
  ZH_CN("zh-CN", "zh-CN"),
  EN("en", "en");

  companion object {
    val default: TranslationTargetLanguage = ZH_CN

    fun fromPersistedValue(raw: String?): TranslationTargetLanguage =
        entries.firstOrNull { it.persistedValue == raw?.trim() } ?: default
  }
}
