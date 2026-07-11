package ddd.kc.ui.pages.settings

import ddd.kc.data.local.settings.AppLanguage
import ddd.kc.data.local.settings.AppPreferences
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.local.settings.DownloadFileNameMode
import ddd.kc.data.local.settings.DownloadSubfolderMode
import ddd.kc.data.local.settings.ThemeMode
import ddd.kc.data.model.OpenAiTranslationConfig
import ddd.kc.data.model.TranslationProvider
import ddd.kc.data.model.TranslationSettings
import ddd.kc.data.model.TranslationTargetLanguage
import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal data class SettingsDraft(
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val cardWidthInput: String,
    val pawchiveBaseUrl: String,
    val translationEnabled: Boolean,
    val translationProvider: TranslationProvider,
    val translationTargetLanguage: TranslationTargetLanguage,
    val chunkWordLimitInput: String,
    val maxConcurrencyInput: String,
    val openAiBaseUrl: String,
    val openAiApiKey: String,
    val openAiModel: String,
    val openAiPromptTemplate: String,
    val downloadSavePath: String,
    val downloadAllowMediaIndexing: Boolean,
    val downloadSubfolderMode: DownloadSubfolderMode,
    val downloadFileNameMode: DownloadFileNameMode,
    val downloadCustomFileNameTemplate: String,
) {
  fun validationError(): SettingsValidationError? {
    val cardWidth =
        cardWidthInput.toIntOrNull() ?: return SettingsValidationError.CARD_WIDTH_NOT_NUMBER
    val chunkWordLimit =
        chunkWordLimitInput.toIntOrNull() ?: return SettingsValidationError.CHUNK_LIMIT_NOT_NUMBER
    val maxConcurrency =
        maxConcurrencyInput.toIntOrNull() ?: return SettingsValidationError.CONCURRENCY_NOT_NUMBER
    if (cardWidth !in AppSettings.CELL_MIN_WIDTH_MIN..AppSettings.CELL_MIN_WIDTH_MAX) {
      return SettingsValidationError.CARD_WIDTH_OUT_OF_RANGE
    }
    val normalizedPawchiveBaseUrl = pawchiveBaseUrl.trim()
    if (normalizedPawchiveBaseUrl.isBlank()) return SettingsValidationError.PAWCHIVE_URL_EMPTY
    val parsedPawchiveUrl =
        try {
          Url(normalizedPawchiveBaseUrl)
        } catch (_: IllegalArgumentException) {
          return SettingsValidationError.PAWCHIVE_URL_INVALID
        }
    if (parsedPawchiveUrl.protocol != URLProtocol.HTTPS) {
      return SettingsValidationError.PAWCHIVE_URL_NOT_HTTPS
    }
    if (
        parsedPawchiveUrl.host.isBlank() ||
            (parsedPawchiveUrl.encodedPath.isNotBlank() && parsedPawchiveUrl.encodedPath != "/") ||
            parsedPawchiveUrl.parameters.entries().isNotEmpty() ||
            parsedPawchiveUrl.fragment.isNotEmpty()
    ) {
      return SettingsValidationError.PAWCHIVE_URL_INVALID
    }
    if (
        chunkWordLimit !in
            AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MIN..AppSettings
                    .TRANSLATION_CHUNK_WORD_LIMIT_MAX
    ) {
      return SettingsValidationError.CHUNK_LIMIT_OUT_OF_RANGE
    }
    if (
        maxConcurrency !in
            AppSettings.TRANSLATION_MAX_CONCURRENCY_MIN..AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX
    ) {
      return SettingsValidationError.CONCURRENCY_OUT_OF_RANGE
    }
    if (translationEnabled && translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
      if (openAiBaseUrl.isBlank()) return SettingsValidationError.OPENAI_URL_EMPTY
      if (!openAiBaseUrl.startsWith("https://")) {
        return SettingsValidationError.OPENAI_URL_NOT_HTTPS
      }
      if (openAiApiKey.isBlank()) return SettingsValidationError.OPENAI_API_KEY_EMPTY
      if (openAiModel.isBlank()) return SettingsValidationError.OPENAI_MODEL_EMPTY
      if (openAiPromptTemplate.isBlank()) return SettingsValidationError.OPENAI_PROMPT_EMPTY
    }
    return null
  }

  fun toTranslationSettings(): TranslationSettings =
      TranslationSettings(
          enabled = translationEnabled,
          provider = translationProvider,
          targetLanguageCode = translationTargetLanguage.languageCode,
          chunkWordLimit = chunkWordLimitInput.toInt(),
          maxConcurrency = maxConcurrencyInput.toInt(),
          openAiConfig =
              OpenAiTranslationConfig(
                  baseUrl = openAiBaseUrl,
                  apiKey = openAiApiKey,
                  model = openAiModel,
                  promptTemplate = openAiPromptTemplate,
              ),
      )

  fun toAppPreferences(): AppPreferences =
      AppPreferences(
          cellMinWidthDp = cardWidthInput.toInt(),
          downloadSavePath = downloadSavePath,
          downloadAllowMediaIndexing = downloadAllowMediaIndexing,
          downloadSubfolderMode = downloadSubfolderMode,
          downloadFileNameMode = downloadFileNameMode,
          downloadCustomFileNameTemplate = downloadCustomFileNameTemplate,
          pawchiveBaseUrl = pawchiveBaseUrl,
          themeMode = themeMode,
          language = language,
          translationSettings = toTranslationSettings(),
      )

  companion object {
    fun fromAppSettings(
        settings: AppSettings,
        translationSettings: TranslationSettings,
    ): SettingsDraft =
        SettingsDraft(
            themeMode = settings.themeMode(),
            language = settings.language(),
            cardWidthInput = settings.cellMinWidthDp().toString(),
            pawchiveBaseUrl = settings.baseUrl(),
            translationEnabled = translationSettings.enabled,
            translationProvider = translationSettings.provider,
            translationTargetLanguage =
                TranslationTargetLanguage.fromPersistedValue(
                    translationSettings.targetLanguageCode
                ),
            chunkWordLimitInput = translationSettings.chunkWordLimit.toString(),
            maxConcurrencyInput = translationSettings.maxConcurrency.toString(),
            openAiBaseUrl = translationSettings.openAiConfig.baseUrl,
            openAiApiKey = translationSettings.openAiConfig.apiKey,
            openAiModel = translationSettings.openAiConfig.model,
            openAiPromptTemplate = translationSettings.openAiConfig.promptTemplate,
            downloadSavePath = settings.downloadSavePath(),
            downloadAllowMediaIndexing = settings.downloadAllowMediaIndexing(),
            downloadSubfolderMode = settings.downloadSubfolderMode(),
            downloadFileNameMode = settings.downloadFileNameMode(),
            downloadCustomFileNameTemplate = settings.downloadCustomFileNameTemplate(),
        )
  }
}

internal enum class SettingsValidationError {
  CARD_WIDTH_NOT_NUMBER,
  CHUNK_LIMIT_NOT_NUMBER,
  CONCURRENCY_NOT_NUMBER,
  CARD_WIDTH_OUT_OF_RANGE,
  PAWCHIVE_URL_EMPTY,
  PAWCHIVE_URL_NOT_HTTPS,
  PAWCHIVE_URL_INVALID,
  CHUNK_LIMIT_OUT_OF_RANGE,
  CONCURRENCY_OUT_OF_RANGE,
  OPENAI_URL_EMPTY,
  OPENAI_URL_NOT_HTTPS,
  OPENAI_API_KEY_EMPTY,
  OPENAI_MODEL_EMPTY,
  OPENAI_PROMPT_EMPTY,
}
