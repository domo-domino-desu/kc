package ddd.kc.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ddd.kc.data.i18n.AppLanguage
import ddd.kc.data.model.Platform
import ddd.kc.data.translation.OpenAiTranslationConfig
import ddd.kc.data.translation.TranslationProvider
import ddd.kc.data.translation.TranslationSettings
import ddd.kc.data.translation.TranslationTargetLanguage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppSettings(private val dataStore: DataStore<Preferences>) {

  companion object {
    private val CELL_MIN_WIDTH_DP = intPreferencesKey("cell_min_width_dp")
    private val DOWNLOAD_SAVE_PATH = stringPreferencesKey("download_save_path")
    private val DOWNLOAD_ALLOW_MEDIA_INDEXING =
        booleanPreferencesKey("download_allow_media_indexing")
    private val DOWNLOAD_SUBFOLDER_MODE = stringPreferencesKey("download_subfolder_mode")
    private val DOWNLOAD_FILE_NAME_MODE = stringPreferencesKey("download_file_name_mode")
    private val DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE =
        stringPreferencesKey("download_custom_file_name_template")
    private val PAWCHIVE_BASE_URL = stringPreferencesKey("pawchive_base_url")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val UI_LANGUAGE = stringPreferencesKey("ui_language")
    private val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
    private val TRANSLATION_PROVIDER = stringPreferencesKey("translation_provider")
    private val TRANSLATION_TARGET_LANG = stringPreferencesKey("translation_target_lang")
    private val TRANSLATION_CHUNK_WORD_LIMIT = intPreferencesKey("translation_chunk_word_limit")
    private val TRANSLATION_MAX_CONCURRENCY = intPreferencesKey("translation_max_concurrency")
    private val OPENAI_TRANSLATION_BASE_URL = stringPreferencesKey("openai_translation_base_url")
    private val OPENAI_TRANSLATION_API_KEY = stringPreferencesKey("openai_translation_api_key")
    private val OPENAI_TRANSLATION_MODEL = stringPreferencesKey("openai_translation_model")
    private val OPENAI_TRANSLATION_PROMPT = stringPreferencesKey("openai_translation_prompt")

    const val CELL_MIN_WIDTH_DEFAULT = 220
    const val CELL_MIN_WIDTH_MIN = 120
    const val CELL_MIN_WIDTH_MAX = 480
    const val TRANSLATION_CHUNK_WORD_LIMIT_DEFAULT = 1024
    const val TRANSLATION_CHUNK_WORD_LIMIT_MIN = 50
    const val TRANSLATION_CHUNK_WORD_LIMIT_MAX = 10000
    const val TRANSLATION_MAX_CONCURRENCY_DEFAULT = 3
    const val TRANSLATION_MAX_CONCURRENCY_MIN = 1
    const val TRANSLATION_MAX_CONCURRENCY_MAX = 8
    const val DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT = "{post_id}-{title}"
    const val PAWCHIVE_ALT_BASE_URL = "https://pawchive.pw"

    val supportedPawchiveBaseUrls = listOf(Platform.PAWCHIVE.defaultBaseUrl, PAWCHIVE_ALT_BASE_URL)
    val supportedThemeModes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val supportedLanguages = listOf(AppLanguage.SYSTEM, AppLanguage.ZH_HANS, AppLanguage.EN)
    val supportedDownloadSubfolderModes =
        listOf(DownloadSubfolderMode.FLAT, DownloadSubfolderMode.BY_USERNAME)
    val supportedDownloadFileNameModes =
        listOf(
            DownloadFileNameMode.ID_TITLE,
            DownloadFileNameMode.USERNAME_ID,
            DownloadFileNameMode.USERNAME_ID_TITLE,
            DownloadFileNameMode.CUSTOM,
        )
  }

  val activePlatformFlow: StateFlow<Platform> =
      kotlinx.coroutines.flow.MutableStateFlow(Platform.PAWCHIVE)

  fun activePlatform(): Platform = Platform.PAWCHIVE

  fun setActivePlatform(platform: Platform) {}

  suspend fun setActivePlatformPersisted(platform: Platform) {}

  private var _cellMinWidthDp: Int = CELL_MIN_WIDTH_DEFAULT
  private var _downloadSavePath: String = ""
  private var _downloadAllowMediaIndexing: Boolean = true
  private var _downloadSubfolderMode: DownloadSubfolderMode = DownloadSubfolderMode.FLAT
  private var _downloadFileNameMode: DownloadFileNameMode = DownloadFileNameMode.ID_TITLE
  private var _downloadCustomFileNameTemplate: String = DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT
  private var _pawchiveBaseUrl: String = Platform.PAWCHIVE.defaultBaseUrl
  private var _themeMode: ThemeMode = ThemeMode.SYSTEM
  private var _language: AppLanguage = AppLanguage.SYSTEM
  private var _translationSettings: TranslationSettings = TranslationSettings()

  suspend fun init() {
    val prefs = dataStore.data.first()
    prefs[CELL_MIN_WIDTH_DP]?.let {
      _cellMinWidthDp = it.coerceIn(CELL_MIN_WIDTH_MIN, CELL_MIN_WIDTH_MAX)
    }
    prefs[DOWNLOAD_SAVE_PATH]?.let { _downloadSavePath = it.trim() }
    prefs[DOWNLOAD_ALLOW_MEDIA_INDEXING]?.let { _downloadAllowMediaIndexing = it }
    prefs[DOWNLOAD_SUBFOLDER_MODE]?.let {
      _downloadSubfolderMode = DownloadSubfolderMode.fromPersistedValue(it)
    }
    prefs[DOWNLOAD_FILE_NAME_MODE]?.let {
      _downloadFileNameMode = DownloadFileNameMode.fromPersistedValue(it)
    }
    prefs[DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE]?.let {
      _downloadCustomFileNameTemplate =
          it.trim().ifBlank { DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT }
    }
    prefs[PAWCHIVE_BASE_URL]?.let { _pawchiveBaseUrl = normalizeBaseUrl(it) }
    prefs[THEME_MODE]?.let { _themeMode = ThemeMode.fromPersistedValue(it) ?: ThemeMode.SYSTEM }
    prefs[UI_LANGUAGE]?.let { _language = AppLanguage.fromPersistedValue(it) }
    _translationSettings =
        TranslationSettings(
            enabled = prefs[TRANSLATION_ENABLED] ?: true,
            provider = TranslationProvider.fromPersistedValue(prefs[TRANSLATION_PROVIDER]),
            targetLanguageCode =
                TranslationTargetLanguage.fromPersistedValue(prefs[TRANSLATION_TARGET_LANG])
                    .languageCode,
            chunkWordLimit =
                (prefs[TRANSLATION_CHUNK_WORD_LIMIT] ?: TRANSLATION_CHUNK_WORD_LIMIT_DEFAULT)
                    .coerceIn(
                        TRANSLATION_CHUNK_WORD_LIMIT_MIN,
                        TRANSLATION_CHUNK_WORD_LIMIT_MAX,
                    ),
            maxConcurrency =
                (prefs[TRANSLATION_MAX_CONCURRENCY] ?: TRANSLATION_MAX_CONCURRENCY_DEFAULT)
                    .coerceIn(
                        TRANSLATION_MAX_CONCURRENCY_MIN,
                        TRANSLATION_MAX_CONCURRENCY_MAX,
                    ),
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

  fun baseUrl(platform: Platform = Platform.PAWCHIVE): String =
      when (platform) {
        Platform.PAWCHIVE -> _pawchiveBaseUrl
      }

  fun cdnUrl(platform: Platform = Platform.PAWCHIVE): String =
      baseUrl(platform).replace("://", "://img.")

  suspend fun setBaseUrl(platform: Platform, url: String) {
    val normalized = normalizeBaseUrl(url)
    when (platform) {
      Platform.PAWCHIVE -> _pawchiveBaseUrl = normalized
    }
    dataStore.edit { prefs ->
      when (platform) {
        Platform.PAWCHIVE -> prefs[PAWCHIVE_BASE_URL] = normalized
      }
    }
  }

  fun baseUrlFlow(platform: Platform) =
      dataStore.data.map { prefs ->
        val url =
            when (platform) {
              Platform.PAWCHIVE -> prefs[PAWCHIVE_BASE_URL]
            }?.let(::normalizeBaseUrl) ?: platform.defaultBaseUrl
        when (platform) {
          Platform.PAWCHIVE -> _pawchiveBaseUrl = url
        }
        url
      }

  fun cellMinWidthDp(): Int = _cellMinWidthDp

  fun cellMinWidthDpFlow() =
      dataStore.data.map { prefs ->
        (prefs[CELL_MIN_WIDTH_DP] ?: CELL_MIN_WIDTH_DEFAULT)
            .coerceIn(CELL_MIN_WIDTH_MIN, CELL_MIN_WIDTH_MAX)
            .also { _cellMinWidthDp = it }
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

  fun downloadAllowMediaIndexing(): Boolean = _downloadAllowMediaIndexing

  fun downloadAllowMediaIndexingFlow() =
      dataStore.data.map { prefs ->
        (prefs[DOWNLOAD_ALLOW_MEDIA_INDEXING] ?: true).also { _downloadAllowMediaIndexing = it }
      }

  suspend fun setDownloadAllowMediaIndexing(allow: Boolean) {
    _downloadAllowMediaIndexing = allow
    dataStore.edit { it[DOWNLOAD_ALLOW_MEDIA_INDEXING] = allow }
  }

  fun downloadSubfolderMode(): DownloadSubfolderMode = _downloadSubfolderMode

  fun downloadSubfolderModeFlow() =
      dataStore.data.map { prefs ->
        DownloadSubfolderMode.fromPersistedValue(prefs[DOWNLOAD_SUBFOLDER_MODE]).also {
          _downloadSubfolderMode = it
        }
      }

  suspend fun setDownloadSubfolderMode(mode: DownloadSubfolderMode) {
    _downloadSubfolderMode = mode
    dataStore.edit { it[DOWNLOAD_SUBFOLDER_MODE] = mode.persistedValue }
  }

  fun downloadFileNameMode(): DownloadFileNameMode = _downloadFileNameMode

  fun downloadFileNameModeFlow() =
      dataStore.data.map { prefs ->
        DownloadFileNameMode.fromPersistedValue(prefs[DOWNLOAD_FILE_NAME_MODE]).also {
          _downloadFileNameMode = it
        }
      }

  suspend fun setDownloadFileNameMode(mode: DownloadFileNameMode) {
    _downloadFileNameMode = mode
    dataStore.edit { it[DOWNLOAD_FILE_NAME_MODE] = mode.persistedValue }
  }

  fun downloadCustomFileNameTemplate(): String = _downloadCustomFileNameTemplate

  fun downloadCustomFileNameTemplateFlow() =
      dataStore.data.map { prefs ->
        (prefs[DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE] ?: DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT)
            .trim()
            .ifBlank { DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT }
            .also { _downloadCustomFileNameTemplate = it }
      }

  suspend fun setDownloadCustomFileNameTemplate(template: String) {
    val normalized = template.trim().ifBlank { DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT }
    _downloadCustomFileNameTemplate = normalized
    dataStore.edit { it[DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE] = normalized }
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

  fun translationSettingsFlow() =
      dataStore.data.map { prefs ->
        TranslationSettings(
                enabled = prefs[TRANSLATION_ENABLED] ?: true,
                provider = TranslationProvider.fromPersistedValue(prefs[TRANSLATION_PROVIDER]),
                targetLanguageCode =
                    TranslationTargetLanguage.fromPersistedValue(prefs[TRANSLATION_TARGET_LANG])
                        .languageCode,
                chunkWordLimit =
                    (prefs[TRANSLATION_CHUNK_WORD_LIMIT] ?: TRANSLATION_CHUNK_WORD_LIMIT_DEFAULT)
                        .coerceIn(
                            TRANSLATION_CHUNK_WORD_LIMIT_MIN,
                            TRANSLATION_CHUNK_WORD_LIMIT_MAX,
                        ),
                maxConcurrency =
                    (prefs[TRANSLATION_MAX_CONCURRENCY] ?: TRANSLATION_MAX_CONCURRENCY_DEFAULT)
                        .coerceIn(
                            TRANSLATION_MAX_CONCURRENCY_MIN,
                            TRANSLATION_MAX_CONCURRENCY_MAX,
                        ),
                openAiConfig =
                    OpenAiTranslationConfig(
                        baseUrl =
                            prefs[OPENAI_TRANSLATION_BASE_URL]
                                ?: OpenAiTranslationConfig.defaultBaseUrl,
                        apiKey = prefs[OPENAI_TRANSLATION_API_KEY] ?: "",
                        model =
                            prefs[OPENAI_TRANSLATION_MODEL] ?: OpenAiTranslationConfig.defaultModel,
                        promptTemplate =
                            prefs[OPENAI_TRANSLATION_PROMPT]
                                ?: OpenAiTranslationConfig.defaultPromptTemplate,
                    ),
            )
            .also { _translationSettings = it }
      }

  suspend fun setTranslationSettings(settings: TranslationSettings) {
    val normalized =
        settings.copy(
            chunkWordLimit =
                settings.chunkWordLimit.coerceIn(
                    TRANSLATION_CHUNK_WORD_LIMIT_MIN,
                    TRANSLATION_CHUNK_WORD_LIMIT_MAX,
                ),
            maxConcurrency =
                settings.maxConcurrency.coerceIn(
                    TRANSLATION_MAX_CONCURRENCY_MIN,
                    TRANSLATION_MAX_CONCURRENCY_MAX,
                ),
        )
    _translationSettings = normalized
    dataStore.edit { prefs ->
      prefs[TRANSLATION_ENABLED] = normalized.enabled
      prefs[TRANSLATION_PROVIDER] = normalized.provider.persistedValue
      prefs[TRANSLATION_TARGET_LANG] = normalized.targetLanguageCode
      prefs[TRANSLATION_CHUNK_WORD_LIMIT] = normalized.chunkWordLimit
      prefs[TRANSLATION_MAX_CONCURRENCY] = normalized.maxConcurrency
      prefs[OPENAI_TRANSLATION_BASE_URL] = normalized.openAiConfig.baseUrl
      prefs[OPENAI_TRANSLATION_API_KEY] = normalized.openAiConfig.apiKey
      prefs[OPENAI_TRANSLATION_MODEL] = normalized.openAiConfig.model
      prefs[OPENAI_TRANSLATION_PROMPT] = normalized.openAiConfig.promptTemplate
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

  private fun normalizeBaseUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    return trimmed.ifBlank { Platform.PAWCHIVE.defaultBaseUrl }
  }
}
