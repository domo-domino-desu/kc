package ddd.kc.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ddd.kc.data.i18n.AppLanguage
import ddd.kc.data.security.SecretStore
import ddd.kc.data.translation.OpenAiTranslationConfig
import ddd.kc.data.translation.TranslationProvider
import ddd.kc.data.translation.TranslationSettings
import ddd.kc.data.translation.TranslationTargetLanguage
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppSettings(
    private val dataStore: DataStore<Preferences>,
    private val secretStore: SecretStore,
) {
  private val preferences = MutableStateFlow(defaultPreferences())
  private val _loadState = MutableStateFlow<SettingsLoadState>(SettingsLoadState.Loading)
  val loadState: StateFlow<SettingsLoadState> = _loadState

  suspend fun init() {
    if (_loadState.value is SettingsLoadState.Ready) return
    try {
      val persisted = dataStore.data.first()
      val loaded = decode(persisted)
      val legacyApiKey = persisted[OPENAI_TRANSLATION_API_KEY]?.trim().orEmpty()
      if (legacyApiKey.isNotBlank()) {
        secretStore.put(OPENAI_API_KEY_SECRET, legacyApiKey)
        dataStore.edit { it.remove(OPENAI_TRANSLATION_API_KEY) }
      }
      publish(loaded)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      _loadState.value = SettingsLoadState.Error(error)
    }
  }

  fun snapshot(): AppPreferences = preferences.value

  fun baseUrl(): String = preferences.value.pawchiveBaseUrl

  fun cdnUrl(): String = baseUrl().replace("://", "://img.")

  fun baseUrlFlow() = preferences.map { it.pawchiveBaseUrl }.distinctUntilChanged()

  fun cellMinWidthDp(): Int = preferences.value.cellMinWidthDp

  fun cellMinWidthDpFlow() = preferences.map { it.cellMinWidthDp }.distinctUntilChanged()

  fun downloadSavePath(): String = preferences.value.downloadSavePath

  fun downloadSavePathFlow() = preferences.map { it.downloadSavePath }.distinctUntilChanged()

  fun downloadAllowMediaIndexing(): Boolean = preferences.value.downloadAllowMediaIndexing

  fun downloadAllowMediaIndexingFlow() =
      preferences.map { it.downloadAllowMediaIndexing }.distinctUntilChanged()

  fun downloadSubfolderMode(): DownloadSubfolderMode = preferences.value.downloadSubfolderMode

  fun downloadSubfolderModeFlow() =
      preferences.map { it.downloadSubfolderMode }.distinctUntilChanged()

  fun downloadFileNameMode(): DownloadFileNameMode = preferences.value.downloadFileNameMode

  fun downloadFileNameModeFlow() =
      preferences.map { it.downloadFileNameMode }.distinctUntilChanged()

  fun downloadCustomFileNameTemplate(): String = preferences.value.downloadCustomFileNameTemplate

  fun downloadCustomFileNameTemplateFlow() =
      preferences.map { it.downloadCustomFileNameTemplate }.distinctUntilChanged()

  fun themeMode(): ThemeMode = preferences.value.themeMode

  fun themeModeFlow() = preferences.map { it.themeMode }.distinctUntilChanged()

  fun language(): AppLanguage = preferences.value.language

  fun languageFlow() = preferences.map { it.language }.distinctUntilChanged()

  fun translationSettings(): TranslationSettings = preferences.value.translationSettings

  fun translationSettingsFlow() = preferences.map { it.translationSettings }.distinctUntilChanged()

  /** Commits the complete non-secret snapshot in one DataStore transaction. */
  suspend fun save(value: AppPreferences) {
    val normalized = normalize(value)
    val apiKey = normalized.translationSettings.openAiConfig.apiKey.trim()
    if (apiKey.isBlank()) secretStore.delete(OPENAI_API_KEY_SECRET)
    else secretStore.put(OPENAI_API_KEY_SECRET, apiKey)

    dataStore.edit { persisted ->
      persisted[CELL_MIN_WIDTH_DP] = normalized.cellMinWidthDp
      persisted[DOWNLOAD_SAVE_PATH] = normalized.downloadSavePath
      persisted[DOWNLOAD_ALLOW_MEDIA_INDEXING] = normalized.downloadAllowMediaIndexing
      persisted[DOWNLOAD_SUBFOLDER_MODE] = normalized.downloadSubfolderMode.persistedValue
      persisted[DOWNLOAD_FILE_NAME_MODE] = normalized.downloadFileNameMode.persistedValue
      persisted[DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE] = normalized.downloadCustomFileNameTemplate
      persisted[PAWCHIVE_BASE_URL] = normalized.pawchiveBaseUrl
      persisted[THEME_MODE] = normalized.themeMode.persistedValue
      persisted[UI_LANGUAGE] = normalized.language.persistedValue
      val translation = normalized.translationSettings
      persisted[TRANSLATION_ENABLED] = translation.enabled
      persisted[TRANSLATION_PROVIDER] = translation.provider.persistedValue
      persisted[TRANSLATION_TARGET_LANG] = translation.targetLanguageCode
      persisted[TRANSLATION_CHUNK_WORD_LIMIT] = translation.chunkWordLimit
      persisted[TRANSLATION_MAX_CONCURRENCY] = translation.maxConcurrency
      persisted[OPENAI_TRANSLATION_BASE_URL] = translation.openAiConfig.baseUrl
      persisted.remove(OPENAI_TRANSLATION_API_KEY)
      persisted[OPENAI_TRANSLATION_MODEL] = translation.openAiConfig.model
      persisted[OPENAI_TRANSLATION_PROMPT] = translation.openAiConfig.promptTemplate
    }
    publish(normalized)
  }

  private fun publish(value: AppPreferences) {
    preferences.value = value
    _loadState.value = SettingsLoadState.Ready(value)
  }

  private fun decode(persisted: Preferences): AppPreferences {
    val legacyApiKey = persisted[OPENAI_TRANSLATION_API_KEY].orEmpty()
    return normalize(
        AppPreferences(
            cellMinWidthDp = persisted[CELL_MIN_WIDTH_DP] ?: CELL_MIN_WIDTH_DEFAULT,
            downloadSavePath = persisted[DOWNLOAD_SAVE_PATH].orEmpty(),
            downloadAllowMediaIndexing = persisted[DOWNLOAD_ALLOW_MEDIA_INDEXING] ?: true,
            downloadSubfolderMode =
                DownloadSubfolderMode.fromPersistedValue(persisted[DOWNLOAD_SUBFOLDER_MODE]),
            downloadFileNameMode =
                DownloadFileNameMode.fromPersistedValue(persisted[DOWNLOAD_FILE_NAME_MODE]),
            downloadCustomFileNameTemplate =
                persisted[DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE]
                    ?: DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT,
            pawchiveBaseUrl = normalizePersistedBaseUrl(persisted[PAWCHIVE_BASE_URL].orEmpty()),
            themeMode = ThemeMode.fromPersistedValue(persisted[THEME_MODE]) ?: ThemeMode.SYSTEM,
            language = AppLanguage.fromPersistedValue(persisted[UI_LANGUAGE]),
            translationSettings =
                TranslationSettings(
                    enabled = persisted[TRANSLATION_ENABLED] ?: true,
                    provider =
                        TranslationProvider.fromPersistedValue(persisted[TRANSLATION_PROVIDER]),
                    targetLanguageCode =
                        TranslationTargetLanguage.fromPersistedValue(
                                persisted[TRANSLATION_TARGET_LANG]
                            )
                            .languageCode,
                    chunkWordLimit =
                        persisted[TRANSLATION_CHUNK_WORD_LIMIT]
                            ?: TRANSLATION_CHUNK_WORD_LIMIT_DEFAULT,
                    maxConcurrency =
                        persisted[TRANSLATION_MAX_CONCURRENCY]
                            ?: TRANSLATION_MAX_CONCURRENCY_DEFAULT,
                    openAiConfig =
                        OpenAiTranslationConfig(
                            baseUrl =
                                persisted[OPENAI_TRANSLATION_BASE_URL]
                                    ?: OpenAiTranslationConfig.defaultBaseUrl,
                            apiKey = secretStore.get(OPENAI_API_KEY_SECRET) ?: legacyApiKey,
                            model =
                                persisted[OPENAI_TRANSLATION_MODEL]
                                    ?: OpenAiTranslationConfig.defaultModel,
                            promptTemplate =
                                persisted[OPENAI_TRANSLATION_PROMPT]
                                    ?: OpenAiTranslationConfig.defaultPromptTemplate,
                        ),
                ),
        )
    )
  }

  private fun normalize(value: AppPreferences): AppPreferences =
      value.copy(
          cellMinWidthDp = value.cellMinWidthDp.coerceIn(CELL_MIN_WIDTH_MIN, CELL_MIN_WIDTH_MAX),
          downloadSavePath = value.downloadSavePath.trim(),
          downloadCustomFileNameTemplate =
              value.downloadCustomFileNameTemplate.trim().ifBlank {
                DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT
              },
          pawchiveBaseUrl = normalizeBaseUrl(value.pawchiveBaseUrl),
          translationSettings =
              value.translationSettings.copy(
                  chunkWordLimit =
                      value.translationSettings.chunkWordLimit.coerceIn(
                          TRANSLATION_CHUNK_WORD_LIMIT_MIN,
                          TRANSLATION_CHUNK_WORD_LIMIT_MAX,
                      ),
                  maxConcurrency =
                      value.translationSettings.maxConcurrency.coerceIn(
                          TRANSLATION_MAX_CONCURRENCY_MIN,
                          TRANSLATION_MAX_CONCURRENCY_MAX,
                      ),
              ),
      )

  private fun normalizeBaseUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    require(trimmed.isNotBlank()) { "Pawchive base URL cannot be blank" }
    val parsed = Url(trimmed)
    require(parsed.protocol == URLProtocol.HTTPS) { "Pawchive base URL must use HTTPS" }
    require(parsed.host.isNotBlank()) { "Pawchive base URL must have a host" }
    require(parsed.encodedPath.isBlank() || parsed.encodedPath == "/") {
      "Pawchive base URL must not contain a path"
    }
    require(parsed.parameters.isEmpty()) { "Pawchive base URL must not contain a query" }
    require(parsed.fragment.isEmpty()) { "Pawchive base URL must not contain a fragment" }
    return parsed.toString().trimEnd('/')
  }

  private fun normalizePersistedBaseUrl(value: String): String =
      runCatching { normalizeBaseUrl(value) }.getOrDefault(PAWCHIVE_DEFAULT_BASE_URL)

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
    private const val OPENAI_API_KEY_SECRET = "openai_translation_api_key"

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
    const val PAWCHIVE_DEFAULT_BASE_URL = "https://pawchive.st"
    const val PAWCHIVE_ALT_BASE_URL = "https://pawchive.pw"

    val supportedPawchiveBaseUrls = listOf(PAWCHIVE_DEFAULT_BASE_URL, PAWCHIVE_ALT_BASE_URL)
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

    private fun defaultPreferences() =
        AppPreferences(
            cellMinWidthDp = CELL_MIN_WIDTH_DEFAULT,
            downloadSavePath = "",
            downloadAllowMediaIndexing = true,
            downloadSubfolderMode = DownloadSubfolderMode.FLAT,
            downloadFileNameMode = DownloadFileNameMode.ID_TITLE,
            downloadCustomFileNameTemplate = DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE_DEFAULT,
            pawchiveBaseUrl = PAWCHIVE_DEFAULT_BASE_URL,
            themeMode = ThemeMode.SYSTEM,
            language = AppLanguage.SYSTEM,
            translationSettings = TranslationSettings(),
        )
  }
}
