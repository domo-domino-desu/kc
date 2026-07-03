package ddd.kc.data.settings

enum class DownloadSubfolderMode(val persistedValue: String) {
  FLAT("flat"),
  BY_USERNAME("by_username");

  companion object {
    fun fromPersistedValue(raw: String?): DownloadSubfolderMode =
        entries.firstOrNull { it.persistedValue == raw?.trim() } ?: FLAT
  }
}

enum class DownloadFileNameMode(val persistedValue: String) {
  ID_TITLE("id_title"),
  USERNAME_ID("username_id"),
  USERNAME_ID_TITLE("username_id_title"),
  CUSTOM("custom");

  companion object {
    fun fromPersistedValue(raw: String?): DownloadFileNameMode =
        entries.firstOrNull { it.persistedValue == raw?.trim() } ?: ID_TITLE
  }
}
