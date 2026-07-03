package ddd.kc.data.translation

internal class TranslationResultAligner {
  fun parseChunkTranslation(translated: String, expectedBlockCount: Int): List<String> {
    val normalizedTranslation = translated.replace(fullWidthPercentChar, asciiPercentChar)
    val bySeparator = normalizedTranslation.split(batchSeparator)
    if (bySeparator.size == expectedBlockCount) return bySeparator

    val byMarkerLine = splitBySeparatorLine(normalizedTranslation)
    if (byMarkerLine.size == expectedBlockCount) return byMarkerLine

    val byLineBreak = normalizedTranslation.replace("\r\n", "\n").split('\n')
    if (byLineBreak.size == expectedBlockCount) return byLineBreak

    if (expectedBlockCount == 1) return listOf(normalizedTranslation)
    if (byLineBreak.isEmpty()) return List(expectedBlockCount) { "" }

    if (byLineBreak.size < expectedBlockCount) {
      return buildList {
        addAll(byLineBreak)
        repeat(expectedBlockCount - byLineBreak.size) { add("") }
      }
    }

    return List(expectedBlockCount) { bucket ->
      val start = bucket * byLineBreak.size / expectedBlockCount
      val end = (bucket + 1) * byLineBreak.size / expectedBlockCount
      byLineBreak.subList(start, end).joinToString("\n")
    }
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
