package ddd.kc.desktop

import com.github.panpf.zoomimage.util.requiredMainThread
import com.github.panpf.zoomimage.util.requiredWorkThread
import com.github.panpf.zoomimage.util.setMainThreadChecker
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ZoomImageThreadingTest {
  @Test
  fun taoThreadIsUiAndBackgroundDecodingStillRequiresAWorker() {
    val worker = Executors.newSingleThreadExecutor()
    try {
      installZoomImageMainThreadChecker()
      requiredMainThread()
      assertFailsWith<IllegalStateException> { requiredWorkThread() }
      worker
          .submit {
            requiredWorkThread()
            assertFailsWith<IllegalStateException> { requiredMainThread() }
          }
          .get()
    } finally {
      setMainThreadChecker(null)
      worker.shutdownNow()
    }
  }
}
