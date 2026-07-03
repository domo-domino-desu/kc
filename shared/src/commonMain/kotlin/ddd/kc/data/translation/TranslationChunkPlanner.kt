package ddd.kc.data.translation

internal class TranslationChunkPlanner {
  fun buildChunks(sourceTexts: List<String>, chunkWordLimit: Int): List<TranslationChunk> {
    val normalizedWordLimit = chunkWordLimit.coerceAtLeast(1)
    val chunks = mutableListOf<TranslationChunk>()

    var startIndex = 0
    val current = mutableListOf<String>()
    var currentWords = 0

    sourceTexts.forEachIndexed { index, sourceText ->
      val words = estimateWordCount(sourceText)
      val nextWords = currentWords + if (current.isEmpty()) words else words + separatorWordCost
      val shouldFlush = current.isNotEmpty() && nextWords > normalizedWordLimit

      if (shouldFlush) {
        chunks += TranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
        current.clear()
        currentWords = 0
        startIndex = index
      }

      current += sourceText
      currentWords += if (current.size == 1) words else words + separatorWordCost
    }

    if (current.isNotEmpty()) {
      chunks += TranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
    }

    return chunks
  }

  private fun estimateWordCount(text: String): Int {
    val normalized = text.trim()
    if (normalized.isBlank()) return 0

    val cjkMatches = cjkRegex.findAll(normalized).count()
    val nonCjk = cjkRegex.replace(normalized, " ")
    val tokenMatches = latinWordRegex.findAll(nonCjk).count()

    val words = cjkMatches + tokenMatches
    return words.coerceAtLeast(1)
  }

  private companion object {
    private const val separatorWordCost = 1
    private val cjkRegex =
        Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
    private val latinWordRegex = Regex("[\\p{L}\\p{N}]+")
  }
}
