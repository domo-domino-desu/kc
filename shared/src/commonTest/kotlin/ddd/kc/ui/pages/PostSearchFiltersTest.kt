package ddd.kc.ui.pages

import com.fleeksoft.ksoup.Ksoup
import ddd.kc.data.model.PostSearchFilterError
import ddd.kc.data.model.PostSearchFilters
import ddd.kc.data.model.PostSearchMedia
import ddd.kc.fake.TestFixtures
import ddd.kc.ui.pages.works.PostSearchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostSearchFiltersTest {
  @Test
  fun pendingSearchCanReplaceAnInitialLoad() {
    val loading =
        PostSearchState(paging = ddd.kc.ui.components.paging.OffsetPagingState(loading = true))
    assertFalse(loading.canSubmitSearch)
    assertTrue(loading.copy(draftQuery = "wolf").canSubmitSearch)
    assertTrue(
        loading
            .copy(draftFilters = PostSearchFilters(media = PostSearchMedia.IMAGE))
            .canSubmitSearch
    )
    assertFalse(
        loading
            .copy(draftQuery = "wolf", draftFilters = PostSearchFilters(after = "invalid"))
            .canSubmitSearch
    )
  }

  @Test
  fun structuredFiltersProduceTheQueryUsedByTheRealWebsiteFixture() {
    val filters =
        PostSearchFilters(
            titleOnly = true,
            after = "2026-01-01",
            before = "2026-12-01",
            service = "patreon",
            media = PostSearchMedia.VIDEO,
        )
    val query = filters.query("\"full reaction\" -trailer")
    assertEquals(
        "intitle:\"full reaction\" -intitle:trailer after:2026-01-01 before:2026-12-01 service:patreon has:video",
        query,
    )
    val capturedQuery =
        Ksoup.parse(TestFixtures.read("live/posts-search-syntax.html"))
            .selectFirst("input[name=q]")!!
            .attr("value")
    // The stored fixture also exercises a raw exclusion independent of title-only matching.
    assertTrue(capturedQuery.contains("intitle:\"full reaction\""))
    assertTrue(capturedQuery.contains("service:patreon has:video"))
    assertNull(filters.error)
  }

  @Test
  fun manualSyntaxIsNotRewrittenAndTitleModePreservesOperatorsAndPhrases() {
    val query = "\"exact phrase\" -word -\"two words\" after:2026-01-01 service:fanbox has:archive"
    assertEquals(query, PostSearchFilters().query(query))
    assertEquals(
        "intitle:\"exact phrase\" -intitle:word -intitle:\"two words\" after:2026-01-01 service:fanbox has:archive",
        PostSearchFilters(titleOnly = true).query(query),
    )
  }

  @Test
  fun dateValidationRejectsImpossibleDatesAndReversedOrEmptyRanges() {
    assertEquals(PostSearchFilterError.INVALID_DATE, PostSearchFilters(after = "2026-02-30").error)
    assertEquals(PostSearchFilterError.INVALID_DATE, PostSearchFilters(before = "2026-12").error)
    assertEquals(
        PostSearchFilterError.INVALID_RANGE,
        PostSearchFilters(after = "2026-02-02", before = "2026-02-01").error,
    )
    assertEquals(
        PostSearchFilterError.INVALID_RANGE,
        PostSearchFilters(after = "2026-02-01", before = "2026-02-01").error,
    )
    assertNull(PostSearchFilters(after = "2024-02-29").error)
  }

  @Test
  fun changingOnlyFiltersKeepsAppliedQueryAndMarksResultsPending() {
    val filters = PostSearchFilters(media = PostSearchMedia.ARCHIVE)
    val state = PostSearchState(draftQuery = "wolf", appliedQuery = "wolf", draftFilters = filters)
    assertTrue(state.hasPendingQuery)
    assertEquals("wolf", state.query)
    assertFalse(state.copy(appliedQuery = "wolf has:archive").hasPendingQuery)
    assertEquals("has:archive", filters.query(""))
  }
}
