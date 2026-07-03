package ddd.kc.ui.pages.settings

import ddd.kc.data.i18n.AppLanguage
import ddd.kc.data.settings.AppSettings
import ddd.kc.data.settings.DownloadFileNameMode
import ddd.kc.data.settings.DownloadSubfolderMode
import ddd.kc.data.settings.ThemeMode
import ddd.kc.data.translation.OpenAiTranslationConfig
import ddd.kc.data.translation.TranslationProvider
import ddd.kc.data.translation.TranslationSettings
import ddd.kc.data.translation.TranslationTargetLanguage

internal data class SettingsDraft(
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val cardWidthInput: String,
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
  fun validationMessage(): String? {
    val cardWidth = cardWidthInput.toIntOrNull() ?: return "Card width must be a number"
    val chunkWordLimit =
        chunkWordLimitInput.toIntOrNull() ?: return "Chunk word limit must be a number"
    val maxConcurrency =
        maxConcurrencyInput.toIntOrNull() ?: return "Max concurrency must be a number"
    if (cardWidth !in AppSettings.CELL_MIN_WIDTH_MIN..AppSettings.CELL_MIN_WIDTH_MAX) {
      return "Card width must be ${AppSettings.CELL_MIN_WIDTH_MIN}-${AppSettings.CELL_MIN_WIDTH_MAX} dp"
    }
    if (
        chunkWordLimit !in
            AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MIN..AppSettings
                    .TRANSLATION_CHUNK_WORD_LIMIT_MAX
    ) {
      return "Chunk word limit must be ${AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MIN}-${AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX}"
    }
    if (
        maxConcurrency !in
            AppSettings.TRANSLATION_MAX_CONCURRENCY_MIN..AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX
    ) {
      return "Max concurrency must be ${AppSettings.TRANSLATION_MAX_CONCURRENCY_MIN}-${AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX}"
    }
    if (translationEnabled && translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
      if (openAiBaseUrl.isBlank()) return "Base URL cannot be empty"
      if (!openAiBaseUrl.startsWith("http://") && !openAiBaseUrl.startsWith("https://")) {
        return "Base URL must start with http:// or https://"
      }
      if (openAiApiKey.isBlank()) return "API Key cannot be empty"
      if (openAiModel.isBlank()) return "Model cannot be empty"
      if (openAiPromptTemplate.isBlank()) return "Prompt Template cannot be empty"
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

  companion object {
    fun fromAppSettings(
        settings: AppSettings,
        translationSettings: TranslationSettings,
    ): SettingsDraft =
        SettingsDraft(
            themeMode = settings.themeMode(),
            language = settings.language(),
            cardWidthInput = settings.cellMinWidthDp().toString(),
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
