package ddd.kc.data.remote.network

import ddd.kc.data.model.QueryError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException

class QueryErrorsTest {
  @Test
  fun mapsCloudflareOutcomesIntoTheCanonicalQueryErrorModel() {
    val challenge = PawchiveCfChallengeException("ray-1")
    val mapped = challenge.toQueryError()
    assertIs<QueryError.CfChallenge>(mapped)
    assertEquals("ray-1", mapped.cfRay)
    assertSame(challenge, mapped.cause)

    assertIs<QueryError.ChallengeCancelled>(PawchiveChallengeCancelledException().toQueryError())
  }

  @Test
  fun coroutineCancellationIsNeverConvertedToUiError() {
    val cancellation = CancellationException("cancelled")
    val thrown = assertFailsWith<CancellationException> { cancellation.toQueryError() }
    assertSame(cancellation, thrown)
  }
}
