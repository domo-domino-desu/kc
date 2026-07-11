package ddd.kc.utils.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class SuspendResultTest {
  @Test
  fun `ordinary failure is captured`() = runBlocking {
    val result = resultOfSuspend<Int> { error("boom") }

    assertTrue(result.isFailure)
    assertEquals("boom", result.exceptionOrNull()?.message)
  }

  @Test
  fun `cancellation is never captured`() = runBlocking {
    assertFailsWith<CancellationException> {
      resultOfSuspend<Unit> { throw CancellationException("cancelled") }
    }
    Unit
  }
}
