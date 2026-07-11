package ddd.kc.data.remote.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationResultAlignerTest {
  private val aligner = TranslationResultAligner()

  @Test
  fun `exact markers preserve block identity`() {
    assertEquals(
        listOf("第一块", "第二块"),
        aligner.parseChunkTranslation("第一块\n\n%%\n\n第二块", expectedBlockCount = 2),
    )
  }

  @Test
  fun `missing marker fails closed instead of guessing`() {
    assertFailsWith<TranslationAlignmentException> {
      aligner.parseChunkTranslation("第一行\n第二行\n第三行", expectedBlockCount = 2)
    }
  }

  @Test
  fun `single block may contain arbitrary line breaks`() {
    assertEquals(
        listOf("第一行\n第二行"),
        aligner.parseChunkTranslation("第一行\n第二行", expectedBlockCount = 1),
    )
  }
}
