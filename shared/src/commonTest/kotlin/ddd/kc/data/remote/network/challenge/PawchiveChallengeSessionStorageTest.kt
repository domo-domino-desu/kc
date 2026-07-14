package ddd.kc.data.remote.network.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PawchiveChallengeSessionStorageTest {
  @Test
  fun concurrentAcquisitionsReuseActiveSessionAndTrackTransitions() = runTest {
    val storage = PawchiveChallengeSessionStorage()
    val first = storage.acquire(signal("https://pawchive.st/posts"))
    val second = storage.acquire(signal("https://img.pawchive.st/data/file"))

    assertTrue(first.created)
    assertFalse(second.created)
    assertSame(first.session, second.session)
    storage.markVerifying(first.session)
    assertEquals(
        PawchiveChallengeStatus.Verifying,
        (storage.state.value as PawchiveChallengeUiState.Active).status,
    )
    storage.complete(true)
    assertTrue(first.session.deferred.await())
    assertEquals(PawchiveChallengeUiState.Idle, storage.state.value)
  }

  private fun signal(url: String) = PawchiveChallengeSignal(url, "https://pawchive.st", "ray")
}
