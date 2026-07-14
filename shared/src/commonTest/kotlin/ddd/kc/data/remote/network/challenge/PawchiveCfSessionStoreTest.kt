package ddd.kc.data.remote.network.challenge

import ddd.kc.fake.TestSecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class PawchiveCfSessionStoreTest {
  @Test
  fun isolatesSitesAndPersistsOnlyCloudflareCookies() = runTest {
    val secrets = TestSecretStore()
    val store = PawchiveCfSessionStore(secrets, Json)
    store.merge(
        siteOrigin = "https://pawchive.st",
        rawCookieHeader = "session=secret; cf_clearance=one; __cf_bm=bm",
        userAgent = "Browser/1",
    )
    store.merge(
        siteOrigin = "https://pawchive.pw",
        rawCookieHeader = "cf_clearance=two",
        userAgent = "Browser/2",
    )

    val first = PawchiveCfSessionStore(secrets, Json).load("https://pawchive.st")
    val second = PawchiveCfSessionStore(secrets, Json).load("https://pawchive.pw")
    assertTrue("cf_clearance=one" in first.cookieHeader)
    assertTrue("__cf_bm=bm" in first.cookieHeader)
    assertFalse("session=" in first.cookieHeader)
    assertEquals("Browser/1", first.userAgent)
    assertEquals("cf_clearance=two", second.cookieHeader)
    assertEquals("Browser/2", second.userAgent)
  }

  @Test
  fun baseAndMediaOriginsShareOneTrustGroupWithoutTrustingOtherHosts() {
    assertTrue(
        PawchiveOriginPolicy.isTrustedRequest(
            "https://pawchive.st/api/v1/posts",
            "https://pawchive.st",
        )
    )
    assertTrue(
        PawchiveOriginPolicy.isTrustedRequest(
            "https://img.pawchive.st/data/file",
            "https://pawchive.st",
        )
    )
    assertFalse(
        PawchiveOriginPolicy.isTrustedRequest(
            "https://evil.example/data/file",
            "https://pawchive.st",
        )
    )
  }
}
