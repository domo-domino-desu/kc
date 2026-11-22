package ddd.kc.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import ddd.kc.data.remote.media.installKcCoilImageProgressSupport
import ddd.kc.di.startAppKoin
import ddd.kc.di.stopAppKoin
import ddd.kc.ui.app.KcAppContent
import ddd.kc.ui.app.KcAppEnvironment
import ddd.kc.utils.logging.KcLog
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import okio.Path
import okio.Path.Companion.toPath

private const val coilDiskCacheMaxBytes = 1024L * 1024L * 1024L

fun main() = runDesktopApplication()

internal fun runDesktopApplication(
    decorateContent: @Composable (content: @Composable () -> Unit, close: (Int) -> Unit) -> Unit =
        { content, _ ->
          content()
        },
) {
  System.getenv("KC_E2E_HOME")?.let { root ->
    require(java.io.File(root).isAbsolute) { "E2E profile must be an absolute path" }
    System.setProperty("user.home", root)
  }
  nucleusApplication(backend = NucleusBackend.Tao) {
    installZoomImageMainThreadChecker()
    KcLog.init(KcLog.parseDesktopSeverity(System.getProperty("kc.log.level")))
    startAppKoin(desktopPlatformModule())
    KcAppEnvironment { settingsState ->
      MaterialDecoratedWindow(
          onCloseRequest = {
            stopAppKoin()
            exitApplication()
          },
          title = System.getProperty("e2e.windowTitle", "KC"),
      ) {
        MaterialTitleBar {
          Text(
              text = "KC",
              modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
        setSingletonImageLoaderFactory { platformContext ->
          ImageLoader.Builder(platformContext)
              .installKcCoilImageProgressSupport()
              .diskCache {
                DiskCache.Builder()
                    .directory(coilCachePath())
                    .maxSizeBytes(coilDiskCacheMaxBytes)
                    .build()
              }
              .build()
        }
        decorateContent({ KcAppContent(settingsState = settingsState) }) {
          stopAppKoin()
          kotlin.system.exitProcess(it)
        }
      }
    }
  }
}

private fun coilCachePath(): Path {
  val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
  return "$userHome/.cache/kc/coil-image-cache".toPath()
}
