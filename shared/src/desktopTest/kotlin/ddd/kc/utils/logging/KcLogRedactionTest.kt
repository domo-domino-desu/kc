package ddd.kc.utils.logging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KcLogRedactionTest {
  @Test
  fun redactsAuthorizationCookiesAndUrlQueries() {
    val redacted =
        redactLogText(
            "Authorization: Bearer secret-token session=secret-cookie " +
                "https://example.test/search?q=private&api_key=secret"
        )

    assertFalse("secret-token" in redacted)
    assertFalse("secret-cookie" in redacted)
    assertFalse("private" in redacted)
    assertTrue("<redacted>" in redacted)
  }
}
