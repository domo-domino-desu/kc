package ddd.kc.ui.pages

import ddd.kc.ui.components.paging.OffsetPagingState
import ddd.kc.ui.pages.works.PostSearchState
import ddd.kc.ui.pages.works.appendSearchToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostSearchStateTest {
  @Test
  fun pendingManualSyntaxCanReplaceAnInitialLoad() {
    val loading = PostSearchState(paging = OffsetPagingState(loading = true))
    assertFalse(loading.canSubmitSearch)
    assertTrue(loading.copy(draftQuery = "has:image").canSubmitSearch)
  }

  @Test
  fun editingSyntaxHidesPreviousResultsUntilAnExplicitSearch() {
    val old = PostSearchState(draftQuery = "fox", appliedQuery = "fox")
    val edited = old.copy(draftQuery = appendSearchToken(old.draftQuery, "intitle:"))
    assertTrue(edited.hasPendingQuery)
    assertEquals("fox", edited.query)
    assertFalse(edited.copy(appliedQuery = edited.draftQuery).hasPendingQuery)
    assertFalse(old.copy(draftQuery = "  fox  ").hasPendingQuery)
  }

  @Test
  fun appendedTokensPreserveManualQuotesExclusionsAndWhitespace() {
    assertEquals("intitle:", appendSearchToken("", "intitle:"))
    assertEquals("\"red fox\" -wolf after:", appendSearchToken("\"red fox\" -wolf", "after:"))
    assertEquals("fox  service:", appendSearchToken("fox  ", "service:"))
    assertEquals("fox\nhas:", appendSearchToken("fox\n", "has:"))
    val query = "intitle:\"red fox\" -word after:2026-01 before:2026-12 service:fanbox has:image"
    assertFalse(PostSearchState(draftQuery = query, appliedQuery = query).hasPendingQuery)
  }
}
