package ddd.kc.ui.components.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.local.settings.DownloadFileNameMode
import ddd.kc.data.local.settings.DownloadSubfolderMode
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.fake.TestSecretStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

class DownloadNamingTest {
  @Test
  fun buildsConfiguredSubfolderAndFileName() = runBlocking {
    val settings = tempSettings()
    settings.save(
        settings
            .snapshot()
            .copy(
                downloadSubfolderMode = DownloadSubfolderMode.BY_USERNAME,
                downloadFileNameMode = DownloadFileNameMode.CUSTOM,
                downloadCustomFileNameTemplate = "{username}-{post_id}-{title}-{service}",
            )
    )

    val target =
        buildPostDownloadTarget(
            settings = settings,
            post =
                Post(
                    id = "123",
                    user = "artist/name",
                    service = "patreon",
                    title = "A / bad:name?",
                ),
            file = PostFile(name = "original.PNG", path = "/aa/original.PNG"),
        )

    assertEquals(listOf("artist_name"), target.relativeDirectories)
    assertEquals("artist_name-123-A-bad_name-patreon.png", target.fileName)
  }

  private fun tempSettings(): AppSettings {
    val file = File.createTempFile("kc-download-test", ".preferences_pb")
    file.delete()
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() }),
        TestSecretStore(),
    )
  }
}
