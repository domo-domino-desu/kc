package ddd.kc.data.remote.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRequestGateTest {
  @Test
  fun spacesStartsAndLimitsActiveDownloads() = runTest {
    val gate = MediaRequestGate(nowMillis = { testScheduler.currentTime })
    val starts = mutableListOf<Long>()
    var active = 0
    var peak = 0
    repeat(8) {
      launch {
        gate.execute {
          starts += testScheduler.currentTime
          active++
          peak = maxOf(peak, active)
          delay(10_000)
          active--
        }
      }
    }
    advanceUntilIdle()
    assertEquals(8, starts.size)
    assertEquals(5, peak)
    assertTrue(starts.zipWithNext().all { (first, second) -> second - first >= 1100 })
    assertTrue(starts[5] >= 10_000)
  }

  @Test
  fun cancellationReleasesTheDownloadSlot() = runTest {
    val gate = MediaRequestGate(maxConcurrent = 1, nowMillis = { testScheduler.currentTime })
    val cancelled = launch { gate.execute { awaitCancellation() } }
    runCurrent()
    cancelled.cancelAndJoin()
    assertEquals("next", gate.execute { "next" })
  }

  @Test
  fun failureReleasesTheDownloadSlotWithoutRemovingPacing() = runTest {
    val gate = MediaRequestGate(maxConcurrent = 1, nowMillis = { testScheduler.currentTime })
    assertFailsWith<IllegalStateException> { gate.execute { error("failed") } }
    assertEquals("next", gate.execute { "next" })
    assertEquals(1100, testScheduler.currentTime)
  }
}
