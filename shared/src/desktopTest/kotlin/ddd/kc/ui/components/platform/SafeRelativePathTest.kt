package ddd.kc.ui.components.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeRelativePathTest {
  @Test
  fun acceptsOrdinaryNestedPath() {
    assertTrue(hasSafeRelativePath(listOf("creator", "post-42"), "image.jpg"))
  }

  @Test
  fun rejectsTraversalAndEmbeddedSeparators() {
    assertFalse(hasSafeRelativePath(listOf(".."), "secret.txt"))
    assertFalse(hasSafeRelativePath(listOf("safe/../outside"), "secret.txt"))
    assertFalse(hasSafeRelativePath(emptyList(), "../secret.txt"))
    assertFalse(hasSafeRelativePath(emptyList(), "folder\\secret.txt"))
  }
}
