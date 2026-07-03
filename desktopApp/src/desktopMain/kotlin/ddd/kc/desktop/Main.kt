package ddd.kc.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import ddd.kc.data.media.installKcCoilImageProgressSupport
import ddd.kc.di.startAppKoin
import ddd.kc.di.stopAppKoin
import ddd.kc.ui.app.KcApp
import ddd.kc.utils.logging.KcLog
import okio.Path
import okio.Path.Companion.toPath

private const val coilDiskCacheMaxBytes = 1024L * 1024L * 1024L

fun main() = application {
  KcLog.init(KcLog.parseDesktopSeverity(System.getProperty("kc.log.level")))
  startAppKoin(desktopPlatformModule())
  Window(
      onCloseRequest = {
        stopAppKoin()
        exitApplication()
      },
      title = "KC",
  ) {
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
    KcApp()
  }
}

private fun coilCachePath(): Path {
  val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
  return "$userHome/.cache/kc/coil-image-cache".toPath()
}
