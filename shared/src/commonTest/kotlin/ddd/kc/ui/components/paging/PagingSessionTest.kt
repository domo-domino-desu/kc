package ddd.kc.ui.components.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PagingSessionTest {
  @Test
  fun jumpUpdatesPageImmediatelyAndScrollsToTop() = runTest {
    val session = session { page -> PagingPage(page, listOf("p$page"), lastPage = 5) }
    val effect = async(start = CoroutineStart.UNDISPATCHED) { session.effects.first() }
    session.accept(PagingIntent.JumpToPage(3))
    assertEquals(3, session.state.value.currentPage)
    assertEquals(emptyList(), session.state.value.items)
    runCurrent()
    assertIs<PagingEffect.ScrollToTop>(effect.await())
    advanceUntilIdle()
    assertEquals(listOf("p3"), session.state.value.items)
  }

  @Test
  fun failedJumpRollsBackWindowPageAndViewport() = runTest {
    val session =
        DefaultPagingSession(
            scope = this,
            source = PagingSource { _, _ -> error("failed") },
            keyOf = { it },
            initialPage = PagingPage(2, listOf("old"), lastPage = 5),
        )
    session.accept(PagingIntent.ViewportChanged(PagingAnchor("old", 4, 12)))
    val restore =
        async(start = CoroutineStart.UNDISPATCHED) {
          session.effects.first { it is PagingEffect.RestoreViewport }
        }
    session.accept(PagingIntent.JumpToPage(4))
    advanceUntilIdle()
    assertEquals(2, session.state.value.currentPage)
    assertEquals(listOf("old"), session.state.value.items)
    assertEquals(
        PagingAnchor("old", 4, 12),
        assertIs<PagingEffect.RestoreViewport>(restore.await()).anchor,
    )
  }

  @Test
  fun prependKeepsSelectionByKeyAndRequestsRebase() = runTest {
    val session = session { page -> PagingPage(page, listOf("p$page"), lastPage = 3) }
    session.accept(PagingIntent.JumpToPage(2))
    advanceUntilIdle()
    session.accept(PagingIntent.Select("p2"))
    val rebase =
        async(start = CoroutineStart.UNDISPATCHED) {
          session.effects.first { it is PagingEffect.RebaseSelection }
        }
    session.accept(PagingIntent.Prepend)
    advanceUntilIdle()
    assertEquals("p2", session.state.value.selectedItemKey)
    assertEquals(listOf("p1", "p2"), session.state.value.items)
    assertEquals("p2", assertIs<PagingEffect.RebaseSelection>(rebase.await()).itemKey)
  }

  private fun kotlinx.coroutines.test.TestScope.session(load: suspend (Int) -> PagingPage<String>) =
      DefaultPagingSession(this, PagingSource { page, _ -> load(page) }, { it })
}
