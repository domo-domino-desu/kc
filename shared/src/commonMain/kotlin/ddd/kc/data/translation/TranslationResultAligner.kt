package ddd.kc.data.translation

internal class TranslationAlignmentException(expected: Int, actual: Int) :
    IllegalArgumentException(
        "Translation block alignment failed: expected=$expected, actual=$actual"
    )

internal class TranslationResultAligner {
  fun parseChunkTranslation(translated: String, expectedBlockCount: Int): List<String> {
    val normalizedTranslation = translated.replace(fullWidthPercentChar, asciiPercentChar)
    val bySeparator = normalizedTranslation.split(batchSeparator)
    if (bySeparator.size == expectedBlockCount) return bySeparator

    val byMarkerLine = splitBySeparatorLine(normalizedTranslation)
    if (byMarkerLine.size == expectedBlockCount) return byMarkerLine

    if (expectedBlockCount == 1) return listOf(normalizedTranslation)
    throw TranslationAlignmentException(expectedBlockCount, byMarkerLine.size)
  }

  fun normalizeTranslationText(text: String): String =
      text
          .replace(fullWidthPercentChar, asciiPercentChar)
          .replace(invisibleCharsRegex, "")
          .replace(" ", " ")
          .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
          .replace(Regex(" *\\n *"), "\n")
          .replace(Regex("\\n{3,}"), "\n\n")
          .trim()

  fun sanitizeTranslatedBlockText(text: String): String =
      normalizeTranslationText(text)
          .replace(leadingSeparatorMarkerRegex, "")
          .replace(trailingSeparatorMarkerRegex, "")
          .trim()

  private fun splitBySeparatorLine(translated: String): List<String> {
    val normalized = translated.replace("\r\n", "\n")
    val segments = mutableListOf<String>()
    val current = mutableListOf<String>()

    normalized.lineSequence().forEach { line ->
      if (line.trim() == separatorMarker) {
        segments += current.joinToString("\n")
        current.clear()
      } else {
        current += line
      }
    }

    segments += current.joinToString("\n")
    return segments
  }

  companion object {
    internal const val batchSeparator: String = "\n\n%%\n\n"
    private const val separatorMarker = "%%"
    private const val asciiPercentChar = '%'
    private const val fullWidthPercentChar = '％'
    private val leadingSeparatorMarkerRegex = Regex("""^\s*%%(?:\s|$)+""")
    private val trailingSeparatorMarkerRegex = Regex("""(?:\s|^)%%\s*$""")
    private val invisibleCharsRegex = Regex("[\\u200B-\\u200D\\uFEFF]")
  }
}
