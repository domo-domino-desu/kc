package ddd.kc.ui.pages

import ddd.kc.ui.pages.dm.DmSearchState
import ddd.kc.ui.pages.works.PostSearchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchDraftStateTest {
  @Test
  fun worksSearchKeepsAppliedQueryUntilSubmission() {
    val state = PostSearchState(draftQuery = "new query", appliedQuery = "old query")

    assertEquals("old query", state.query)
    assertTrue(state.hasPendingQuery)
    assertFalse(state.copy(appliedQuery = "new query").hasPendingQuery)
  }

  @Test
  fun dmSearchKeepsAppliedQueryUntilSubmission() {
    val state = DmSearchState(draftQuery = "new query", appliedQuery = "old query")

    assertEquals("old query", state.query)
    assertTrue(state.hasPendingQuery)
    assertFalse(state.copy(appliedQuery = "new query").hasPendingQuery)
  }
}
