package ddd.kc.data.remote.media

import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Original-file server policy: at most one start per second and five active downloads. */
internal class MediaRequestGate(
    private val intervalMillis: Long = 1100,
    maxConcurrent: Int = 5,
    private val nowMillis: () -> Long = monotonicClock(),
) {
  private val permits = Semaphore(maxConcurrent)
  private val starts = Mutex()
  private var nextStart = 0L

  suspend fun <T> execute(block: suspend () -> T): T =
      permits.withPermit {
        starts.withLock {
          val wait = nextStart - nowMillis()
          if (wait > 0) delay(wait)
          nextStart = nowMillis() + intervalMillis
        }
        block()
      }
}

private fun monotonicClock(): () -> Long {
  val origin = TimeSource.Monotonic.markNow()
  return { origin.elapsedNow().inWholeMilliseconds }
}
