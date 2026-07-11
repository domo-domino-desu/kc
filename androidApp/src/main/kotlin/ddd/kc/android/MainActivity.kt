package ddd.kc.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import ddd.kc.data.remote.media.installKcCoilImageProgressSupport
import ddd.kc.ui.app.KcApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okio.Path.Companion.toPath

private const val coilDiskCacheMaxBytes = 1024L * 1024L * 1024L
private val supportedKcHosts =
    setOf("pawchive.st", "www.pawchive.st", "pawchive.pw", "www.pawchive.pw")

class MainActivity : ComponentActivity() {
  private val externalKcLinkEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      emitExternalKcLink(intent)
    }
    enableEdgeToEdge()
    setContent {
      setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .components { add(GifDecoder.Factory()) }
            .installKcCoilImageProgressSupport()
            .diskCache {
              DiskCache.Builder()
                  .directory("${platformContext.cacheDir.absolutePath}/coil-image-cache".toPath())
                  .maxSizeBytes(coilDiskCacheMaxBytes)
                  .build()
            }
            .build()
      }
      KcApp(externalKcLinkEvents = externalKcLinkEvents.asSharedFlow())
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    emitExternalKcLink(intent)
  }

  private fun emitExternalKcLink(intent: Intent?) {
    val uri = extractExternalKcLink(intent) ?: return
    externalKcLinkEvents.tryEmit(uri.toString())
  }

  private fun extractExternalKcLink(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val data = intent.data ?: return null
    val scheme = data.scheme?.lowercase() ?: return null
    if (scheme == "kc") return data
    if (scheme != "https" && scheme != "http") return null
    val host = data.host?.lowercase() ?: return null
    if (host !in supportedKcHosts) return null
    return data
  }
}
