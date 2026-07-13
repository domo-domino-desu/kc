package ddd.kc.data.local.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import ddd.kc.data.model.TranslationProvider
import ddd.kc.data.model.TranslationSettings
import ddd.kc.fake.TestSecretStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

class AppSettingsTest {
  @Test
  fun persistsNewSettingsAndClampsRanges() = runBlocking {
    val settings = tempSettings()

    settings.save(
        settings
            .snapshot()
            .copy(
                language = AppLanguage.EN,
                cellMinWidthDp = AppSettings.CELL_MIN_WIDTH_MAX + 100,
                downloadAllowMediaIndexing = false,
                downloadSubfolderMode = DownloadSubfolderMode.BY_USERNAME,
                downloadFileNameMode = DownloadFileNameMode.USERNAME_ID_TITLE,
                downloadCustomFileNameTemplate = "{username}-{post_id}",
                pawchiveBaseUrl = "https://pawchive.pw/",
                translationSettings =
                    TranslationSettings(
                        enabled = false,
                        provider = TranslationProvider.MICROSOFT,
                        chunkWordLimit = AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX + 1,
                        maxConcurrency = AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX + 1,
                    ),
            )
    )

    assertEquals(AppLanguage.EN, settings.languageFlow().first())
    assertEquals(AppSettings.CELL_MIN_WIDTH_MAX, settings.cellMinWidthDpFlow().first())
    assertFalse(settings.downloadAllowMediaIndexingFlow().first())
    assertEquals(DownloadSubfolderMode.BY_USERNAME, settings.downloadSubfolderModeFlow().first())
    assertEquals(
        DownloadFileNameMode.USERNAME_ID_TITLE,
        settings.downloadFileNameModeFlow().first(),
    )
    assertEquals("{username}-{post_id}", settings.downloadCustomFileNameTemplateFlow().first())
    assertEquals("https://pawchive.pw", settings.baseUrlFlow().first())
    val translation = settings.translationSettingsFlow().first()
    assertFalse(translation.enabled)
    assertEquals(TranslationProvider.MICROSOFT, translation.provider)
    assertEquals(AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX, translation.chunkWordLimit)
    assertEquals(AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX, translation.maxConcurrency)
  }

  @Test
  fun apiKeyIsStoredOnlyInSecretStore() = runBlocking {
    val file = File.createTempFile("kc-secret-test", ".preferences_pb").also(File::delete)
    val dataStore =
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    val secrets = TestSecretStore()
    val settings = AppSettings(dataStore, secrets)
    val translation =
        settings
            .snapshot()
            .translationSettings
            .copy(
                openAiConfig =
                    settings.snapshot().translationSettings.openAiConfig.copy(apiKey = "secret-key")
            )

    settings.save(settings.snapshot().copy(translationSettings = translation))

    assertEquals("secret-key", secrets.get("openai_translation_api_key"))
    assertNull(dataStore.data.first()[stringPreferencesKey("openai_translation_api_key")])
  }

  @Test
  fun redirectUpdateIsAtomicAndPersisted() = runBlocking {
    val file =
        File.createTempFile("kc-redirect-settings-test", ".preferences_pb").also(File::delete)
    val dataStore =
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    val settings = AppSettings(dataStore, TestSecretStore())

    assertEquals(
        true,
        settings.updateBaseUrlFromRedirect(
            expectedBaseUrl = "https://pawchive.st",
            redirectedBaseUrl = "https://pawchive.pw/",
        ),
    )
    assertEquals("https://pawchive.pw", settings.baseUrl())
    assertEquals(
        false,
        settings.updateBaseUrlFromRedirect(
            expectedBaseUrl = "https://pawchive.st",
            redirectedBaseUrl = "https://late.example",
        ),
    )

    val reloaded = AppSettings(dataStore, TestSecretStore())
    reloaded.init()
    assertEquals("https://pawchive.pw", reloaded.baseUrl())
  }

  private fun tempSettings(): AppSettings {
    val file = File.createTempFile("kc-settings-test", ".preferences_pb")
    file.delete()
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() }),
        TestSecretStore(),
    )
  }
}
