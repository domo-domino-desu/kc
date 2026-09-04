package ddd.kc.data.model

enum class AiFilterMode(val persistedValue: String) {
  SHOW("show"),
  HIDE("hide"),
  ONLY("only");

  companion object {
    fun fromPersistedValue(value: String?): AiFilterMode =
        entries.firstOrNull { it.persistedValue == value } ?: SHOW
  }
}
