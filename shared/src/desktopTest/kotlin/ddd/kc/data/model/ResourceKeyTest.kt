package ddd.kc.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ResourceKeyTest {
  @Test
  fun `creator identity includes service`() {
    assertNotEquals(CreatorKey("patreon", "42"), CreatorKey("fanbox", "42"))
  }

  @Test
  fun `post identity includes service creator and post id`() {
    val original = PostKey("patreon", "alice", "42")

    assertNotEquals(original, PostKey("fanbox", "alice", "42"))
    assertNotEquals(original, PostKey("patreon", "bob", "42"))
    assertEquals(original, Post(id = "42", user = "alice", service = "patreon").key)
  }
}
