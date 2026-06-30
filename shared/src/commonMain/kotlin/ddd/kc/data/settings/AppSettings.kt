package ddd.kc.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ddd.kc.application.translation.TranslationSettings
import ddd.kc.data.model.Platform
import ddd.kc.data.translation.OpenAiTranslationConfig
import ddd.kc.data.translation.TranslationProvider
import ddd.kc.data.translation.TranslationTargetLanguage
import ddd.kc.i18n.AppLanguage
import ddd.kc.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class AppSettings(private val dataStore: DataStore<Preferences>) {

  companion object {
    private val CELL_MIN_WIDTH_DP = intPreferencesKey("cell_min_width_dp")
    private val DOWNLOAD_SAVE_PATH = stringPreferencesKey("download_save_path")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val UI_LANGUAGE = stringPreferencesKey("ui_language")
    private val TRANSLATION_PROVIDER = stringPreferencesKey("translation_provider")
    private val TRANSLATION_TARGET_LANG = stringPreferencesKey("translation_target_lang")
    private val TRANSLATION_CHUNK_WORD_LIMIT = intPreferencesKey("translation_chunk_word_limit")
    private val TRANSLATION_MAX_CONCURRENCY = intPreferencesKey("translation_max_concurrency")
    private val OPENAI_TRANSLATION_BASE_URL = stringPreferencesKey("openai_translation_base_url")
    private val OPENAI_TRANSLATION_API_KEY = stringPreferencesKey("openai_translation_api_key")
    private val OPENAI_TRANSLATION_MODEL = stringPreferencesKey("openai_translation_model")
    private val OPENAI_TRANSLATION_PROMPT = stringPreferencesKey("openai_translation_prompt")

    const val CELL_MIN_WIDTH_DEFAULT = 200
    const val CELL_MIN_WIDTH_MIN = 120
    const val CELL_MIN_WIDTH_MAX = 400
  }

  val activePlatformFlow: StateFlow<Platform> =
      kotlinx.coroutines.flow.MutableStateFlow(Platform.PAWCHIVE)

  fun activePlatform(): Platform = Platform.PAWCHIVE

  fun setActivePlatform(platform: Platform) {}

  suspend fun setActivePlatformPersisted(platform: Platform) {}

  private var _cellMinWidthDp: Int = CELL_MIN_WIDTH_DEFAULT
  private var _downloadSavePath: String = ""
  private var _themeMode: ThemeMode = ThemeMode.SYSTEM
  private var _language: AppLanguage = AppLanguage.SYSTEM
  private var _translationSettings: TranslationSettings = TranslationSettings()

  suspend fun init() {
    val prefs = dataStore.data.first()
    prefs[CELL_MIN_WIDTH_DP]?.let { _cellMinWidthDp = it }
    prefs[DOWNLOAD_SAVE_PATH]?.let { _downloadSavePath = it.trim() }
    prefs[THEME_MODE]?.let { _themeMode = ThemeMode.fromPersistedValue(it) ?: ThemeMode.SYSTEM }
    prefs[UI_LANGUAGE]?.let { _language = AppLanguage.fromPersistedValue(it) }
    _translationSettings =
        TranslationSettings(
            provider = TranslationProvider.fromPersistedValue(prefs[TRANSLATION_PROVIDER]),
            targetLanguageCode =
                TranslationTargetLanguage.fromPersistedValue(prefs[TRANSLATION_TARGET_LANG])
                    .languageCode,
            chunkWordLimit = prefs[TRANSLATION_CHUNK_WORD_LIMIT] ?: 1024,
            maxConcurrency = prefs[TRANSLATION_MAX_CONCURRENCY] ?: 3,
            openAiConfig =
                OpenAiTranslationConfig(
                    baseUrl =
                        prefs[OPENAI_TRANSLATION_BASE_URL]
                            ?: OpenAiTranslationConfig.defaultBaseUrl,
                    apiKey = prefs[OPENAI_TRANSLATION_API_KEY] ?: "",
                    model = prefs[OPENAI_TRANSLATION_MODEL] ?: OpenAiTranslationConfig.defaultModel,
                    promptTemplate =
                        prefs[OPENAI_TRANSLATION_PROMPT]
                            ?: OpenAiTranslationConfig.defaultPromptTemplate,
                ),
        )
  }

  fun baseUrl(platform: Platform = Platform.PAWCHIVE): String = Platform.PAWCHIVE.defaultBaseUrl

  fun cdnUrl(platform: Platform = Platform.PAWCHIVE): String =
      baseUrl(platform).replace("://", "://img.")

  suspend fun setBaseUrl(platform: Platform, url: String) {}

  fun baseUrlFlow(platform: Platform) = flowOf(Platform.PAWCHIVE.defaultBaseUrl)

  fun cellMinWidthDp(): Int = _cellMinWidthDp

  fun cellMinWidthDpFlow() =
      dataStore.data.map { prefs ->
        (prefs[CELL_MIN_WIDTH_DP] ?: CELL_MIN_WIDTH_DEFAULT).also { _cellMinWidthDp = it }
      }

  suspend fun setCellMinWidthDp(value: Int) {
    val clamped = value.coerceIn(CELL_MIN_WIDTH_MIN, CELL_MIN_WIDTH_MAX)
    _cellMinWidthDp = clamped
    dataStore.edit { it[CELL_MIN_WIDTH_DP] = clamped }
  }

  fun downloadSavePath(): String = _downloadSavePath

  fun downloadSavePathFlow() =
      dataStore.data.map { prefs ->
        (prefs[DOWNLOAD_SAVE_PATH] ?: "").trim().also { _downloadSavePath = it }
      }

  suspend fun setDownloadSavePath(path: String) {
    val normalized = path.trim()
    _downloadSavePath = normalized
    dataStore.edit { prefs -> prefs[DOWNLOAD_SAVE_PATH] = normalized }
  }

  fun themeMode(): ThemeMode = _themeMode

  fun themeModeFlow() =
      dataStore.data.map { prefs ->
        ThemeMode.fromPersistedValue(prefs[THEME_MODE]).also { _themeMode = it ?: ThemeMode.SYSTEM }
            ?: ThemeMode.SYSTEM
      }

  suspend fun setThemeMode(themeMode: ThemeMode) {
    _themeMode = themeMode
    dataStore.edit { it[THEME_MODE] = themeMode.persistedValue }
  }

  fun translationSettings(): TranslationSettings = _translationSettings

  suspend fun setTranslationSettings(settings: TranslationSettings) {
    _translationSettings = settings
    dataStore.edit { prefs ->
      prefs[TRANSLATION_PROVIDER] = settings.provider.persistedValue
      prefs[TRANSLATION_TARGET_LANG] = settings.targetLanguageCode
      prefs[TRANSLATION_CHUNK_WORD_LIMIT] = settings.chunkWordLimit
      prefs[TRANSLATION_MAX_CONCURRENCY] = settings.maxConcurrency
      prefs[OPENAI_TRANSLATION_BASE_URL] = settings.openAiConfig.baseUrl
      prefs[OPENAI_TRANSLATION_API_KEY] = settings.openAiConfig.apiKey
      prefs[OPENAI_TRANSLATION_MODEL] = settings.openAiConfig.model
      prefs[OPENAI_TRANSLATION_PROMPT] = settings.openAiConfig.promptTemplate
    }
  }

  fun language(): AppLanguage = _language

  fun languageFlow() =
      dataStore.data.map { prefs ->
        AppLanguage.fromPersistedValue(prefs[UI_LANGUAGE]).also { _language = it }
      }

  suspend fun setLanguage(language: AppLanguage) {
    _language = language
    dataStore.edit { it[UI_LANGUAGE] = language.persistedValue }
  }
}
