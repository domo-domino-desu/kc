package ddd.kc.desktop

import com.github.panpf.zoomimage.util.setMainThreadChecker

/** Call from Tao's startup composition before creating any ZoomImage state. */
internal fun installZoomImageMainThreadChecker() {
  val uiThread = Thread.currentThread()
  setMainThreadChecker { Thread.currentThread() === uiThread }
}
