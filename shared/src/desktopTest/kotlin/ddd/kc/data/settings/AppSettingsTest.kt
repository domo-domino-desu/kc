package ddd.kc.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.i18n.AppLanguage
import ddd.kc.data.model.Platform
import ddd.kc.data.translation.TranslationProvider
import ddd.kc.data.translation.TranslationSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

class AppSettingsTest {
  @Test
  fun persistsNewSettingsAndClampsRanges() = runBlocking {
    val settings = tempSettings()

    settings.setLanguage(AppLanguage.EN)
    settings.setCellMinWidthDp(AppSettings.CELL_MIN_WIDTH_MAX + 100)
    settings.setDownloadAllowMediaIndexing(false)
    settings.setDownloadSubfolderMode(DownloadSubfolderMode.BY_USERNAME)
    settings.setDownloadFileNameMode(DownloadFileNameMode.USERNAME_ID_TITLE)
    settings.setDownloadCustomFileNameTemplate("{username}-{post_id}")
    settings.setBaseUrl(Platform.PAWCHIVE, "https://pawchive.pw/")
    settings.setTranslationSettings(
        TranslationSettings(
            enabled = false,
            provider = TranslationProvider.MICROSOFT,
            chunkWordLimit = AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX + 1,
            maxConcurrency = AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX + 1,
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
    assertEquals("https://pawchive.pw", settings.baseUrlFlow(Platform.PAWCHIVE).first())
    val translation = settings.translationSettingsFlow().first()
    assertFalse(translation.enabled)
    assertEquals(TranslationProvider.MICROSOFT, translation.provider)
    assertEquals(AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX, translation.chunkWordLimit)
    assertEquals(AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX, translation.maxConcurrency)
  }

  private fun tempSettings(): AppSettings {
    val file = File.createTempFile("kc-settings-test", ".preferences_pb")
    file.delete()
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    )
  }
}
