package ddd.kc.i18n

enum class AppLanguage(val persistedValue: String) {
  SYSTEM("system"),
  EN("en"),
  ZH_HANS("zh-Hans");

  val resourceLocaleTag: String?
    get() =
        when (this) {
          ZH_HANS -> "zh-CN"
          EN -> "en"
          SYSTEM -> null
        }

  companion object {
    fun fromPersistedValue(value: String?): AppLanguage =
        entries.find { it.persistedValue == value } ?: SYSTEM
  }
}
