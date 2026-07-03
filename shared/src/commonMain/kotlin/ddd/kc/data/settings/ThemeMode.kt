package ddd.kc.data.settings

enum class ThemeMode(val persistedValue: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromPersistedValue(raw: String?): ThemeMode? =
        entries.firstOrNull { it.persistedValue == raw?.trim() }
  }
}
