package ddd.kc.ui.components.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.data.settings.AppSettings
import ddd.kc.data.settings.DownloadFileNameMode
import ddd.kc.data.settings.DownloadSubfolderMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

class DownloadNamingTest {
  @Test
  fun buildsConfiguredSubfolderAndFileName() = runBlocking {
    val settings = tempSettings()
    settings.setDownloadSubfolderMode(DownloadSubfolderMode.BY_USERNAME)
    settings.setDownloadFileNameMode(DownloadFileNameMode.CUSTOM)
    settings.setDownloadCustomFileNameTemplate("{username}-{post_id}-{title}-{service}")

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
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    )
  }
}
