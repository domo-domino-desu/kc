package ddd.kc.data.settings

import ddd.kc.data.i18n.AppLanguage
import ddd.kc.data.translation.TranslationSettings

/** Complete, non-secret application preference snapshot committed as one transaction. */
data class AppPreferences(
    val cellMinWidthDp: Int,
    val downloadSavePath: String,
    val downloadAllowMediaIndexing: Boolean,
    val downloadSubfolderMode: DownloadSubfolderMode,
    val downloadFileNameMode: DownloadFileNameMode,
    val downloadCustomFileNameTemplate: String,
    val pawchiveBaseUrl: String,
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val translationSettings: TranslationSettings,
)

sealed interface SettingsLoadState {
  data object Loading : SettingsLoadState

  data class Ready(val preferences: AppPreferences) : SettingsLoadState

  data class Error(val cause: Throwable) : SettingsLoadState
}
