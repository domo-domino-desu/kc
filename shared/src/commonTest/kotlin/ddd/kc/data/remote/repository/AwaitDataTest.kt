package ddd.kc.data.remote.repository

import ddd.kc.data.model.QueryError
import ddd.kc.data.model.QueryState
import ddd.kc.data.remote.network.QueryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class AwaitDataTest {
  @Test
  fun staleSnapshotDoesNotCancelTheRefreshBeforeItRuns() = runTest {
    var fetched = false
    val result =
        flow {
              emit(QueryState(data = "expired", isRefreshing = true, isStale = true))
              fetched = true
              emit(QueryState(data = "fresh"))
            }
            .awaitData()
    assertEquals("fresh", result)
    assertEquals(true, fetched)
  }

  @Test
  fun freshCacheNeedsNoNetworkAndRefreshFailureIsNotReportedAsSuccess() = runTest {
    assertEquals("cached", flowOf(QueryState(data = "cached")).awaitData())
    assertFailsWith<QueryException> {
      flowOf(
              QueryState(data = "expired", isRefreshing = true),
              QueryState(data = "expired", error = QueryError.Network()),
          )
          .awaitData()
    }
  }
}
