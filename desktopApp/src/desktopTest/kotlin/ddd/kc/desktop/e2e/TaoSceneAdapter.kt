@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DEPRECATION")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package ddd.kc.desktop.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.LocalComposeScene
import dev.nucleusframework.window.tao.LocalTaoWindow
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

/**
 * Attaches to the scene owned and rendered by the production Tao window.
 *
 * CMP 1.11.1 exposes input injection and RootForTest, but Nucleus 2.4.7 does not expose
 * PlatformContext.rootForTestListener. Keep the missing registry adapter here, in test sources.
 * These four field names are version-specific; fail explicitly if CMP changes them. Never create a
 * second/offscreen scene, replace a repository, or drive the OS accessibility bridge. Popup/dialog
 * owners must be included: looking only at mainOwner misses AlertDialog inputs.
 */
internal class TaoSceneAdapter(val scene: ComposeScene) {
  private val uiThread = Thread.currentThread()
  private val recorder = PictureRecorder()
  private var lastFrameNanos = 0L

  /** KWin throttles occluded surfaces; advance this scene without an expensive CPU raster copy. */
  fun advanceFrame() {
    check(Thread.currentThread() === uiThread)
    val now = System.nanoTime()
    if (now - lastFrameNanos < 16_000_000L) return
    val size = scene.size ?: return
    if (size.width <= 0 || size.height <= 0) return
    lastFrameNanos = now
    val canvas = recorder.beginRecording(Rect.makeWH(size.width.toFloat(), size.height.toFloat()))
    scene.render(canvas.asComposeCanvas(), now)
    recorder.finishRecordingAsPicture().close()
  }

  fun dispose() {
    recorder.close()
  }

  init {
    check(scene.javaClass.name == "androidx.compose.ui.scene.CanvasLayersComposeSceneImpl") {
      "Update TaoSceneAdapter for this CMP scene implementation"
    }
    roots()
  }

  fun roots(): List<PlatformRootForTest> {
    check(Thread.currentThread() === uiThread) { "Tao scene accessed outside its UI thread" }
    val main = read(scene, "mainOwner")
    val layers =
        read(scene, "layers") as? List<*>
            ?: error("Update TaoSceneAdapter: CMP popup registry changed")
    val owners = listOf(main) + layers.map { read(checkNotNull(it), "owner") }
    return owners.map { read(it, "rootForTest") as PlatformRootForTest }
  }

  private fun read(instance: Any, field: String): Any =
      try {
        instance.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(instance)
            ?: error("CMP test root field is null: $field")
      } catch (error: ReflectiveOperationException) {
        throw IllegalStateException("Update TaoSceneAdapter for CMP 1.11.1: $field", error)
      }
}

@Composable
internal fun rememberTaoSceneAdapter(): TaoSceneAdapter {
  checkNotNull(LocalTaoWindow.current) { "E2E must run inside the real Tao window" }
  val scene = checkNotNull(LocalComposeScene.current) { "Tao ComposeScene unavailable" }
  return remember(scene) { TaoSceneAdapter(scene) }
}
