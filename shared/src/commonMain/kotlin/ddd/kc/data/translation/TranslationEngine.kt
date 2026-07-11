package ddd.kc.data.translation

import be.digitalia.compose.htmlconverter.htmlToString
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import ddd.kc.data.settings.AppSettings
import ddd.kc.utils.coroutines.resultOfSuspend
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Turns post HTML into translatable blocks, then translates them chunk-by-chunk: split blocks by
 * word budget, dispatch chunks concurrently, and align each provider response back to per-block
 * results.
 */
class TranslationEngine(
    private val dispatcher: TranslationDispatcher,
    private val appSettings: AppSettings,
) {
  private val resultAligner = TranslationResultAligner()

  fun extractBlocks(html: String): List<TranslationBlock> =
      extractTranslationBlocks(html, resultAligner)

  fun isEnabled(): Boolean = appSettings.translationSettings().enabled

  suspend fun translateBlocks(
      blocks: List<TranslationBlock>,
      onBlockResult: (index: Int, result: TranslationBlockResult) -> Unit,
  ) {
    if (blocks.isEmpty()) return
    val settings = appSettings.translationSettings()
    if (!settings.enabled) return

    val chunks = buildChunks(blocks.map { it.sourceText }, settings.chunkWordLimit)
    if (chunks.isEmpty()) return

    coroutineScope {
      val resultChannel =
          Channel<Pair<Int, List<TranslationBlockResult>>>(capacity = chunks.size.coerceAtLeast(1))
      val semaphore = Semaphore(settings.maxConcurrency)

      chunks.forEach { chunk ->
        launch {
          semaphore.withPermit {
            resultChannel.send(
                chunk.startIndex to translateChunk(chunk, settings, dispatcher, resultAligner)
            )
          }
        }
      }

      repeat(chunks.size) {
        val (startIndex, results) = resultChannel.receive()
        results.forEachIndexed { offset, result -> onBlockResult(startIndex + offset, result) }
      }
      resultChannel.close()
    }
  }
}

// --- HTML -> translatable blocks -------------------------------------------------------------

private val standaloneBoundaryTags =
    setOf("p", "blockquote", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "hr")
private val wrapperContainerTags =
    setOf(
        "div",
        "section",
        "article",
        "code",
        "pre",
        "ul",
        "ol",
        "table",
        "tbody",
        "thead",
        "tfoot",
    )

private fun extractTranslationBlocks(
    html: String,
    resultAligner: TranslationResultAligner,
): List<TranslationBlock> {
  if (html.isBlank()) return emptyList()

  val root = Ksoup.parseBodyFragment(html).body()
  val blockHtmlList = mutableListOf<String>()
  collectBlocksFromNodes(nodes = root.childNodes(), wrappers = emptyList(), output = blockHtmlList)

  return blockHtmlList.mapNotNull { blockHtml ->
    val sourceText =
        resultAligner.normalizeTranslationText(htmlToString(html = blockHtml, compactMode = true))
    if (sourceText.isBlank()) null
    else TranslationBlock(originalHtml = blockHtml, sourceText = sourceText)
  }
}

private fun collectBlocksFromNodes(
    nodes: List<Node>,
    wrappers: List<Element>,
    output: MutableList<String>,
) {
  val current = StringBuilder()
  var pendingBreaks = 0

  fun flushCurrent() {
    val html = current.toString().trim()
    if (html.isNotBlank()) output += wrapWithWrappers(innerHtml = html, wrappers = wrappers)
    current.clear()
    pendingBreaks = 0
  }

  nodes.forEach { node ->
    if (node is Element) {
      val tag = node.tagName().lowercase()
      when {
        tag == "br" -> {
          appendNodeHtml(current, node)
          pendingBreaks += 1
          if (pendingBreaks >= 2) flushCurrent()
        }

        tag in standaloneBoundaryTags -> {
          flushCurrent()
          val standalone = node.outerHtml().trim()
          if (standalone.isNotBlank())
              output += wrapWithWrappers(innerHtml = standalone, wrappers = wrappers)
        }

        tag in wrapperContainerTags -> {
          flushCurrent()
          collectBlocksFromNodes(
              nodes = node.childNodes(),
              wrappers = wrappers + listOf(node),
              output = output,
          )
        }

        else -> {
          appendNodeHtml(current, node)
          pendingBreaks = 0
        }
      }
    } else {
      if (pendingBreaks > 0 && node.outerHtml().isBlank()) return@forEach
      appendNodeHtml(current, node)
      pendingBreaks = 0
    }
  }

  flushCurrent()
}

private fun appendNodeHtml(builder: StringBuilder, node: Node) {
  val html = node.outerHtml()
  if (html.isNotBlank()) builder.append(html)
}

private fun wrapWithWrappers(innerHtml: String, wrappers: List<Element>): String {
  var wrapped = innerHtml
  wrappers.asReversed().forEach { wrapper ->
    val tag = wrapper.tagName()
    val openTag = extractOpenTag(wrapper)
    wrapped = "$openTag$wrapped</$tag>"
  }
  return wrapped
}

private fun extractOpenTag(wrapper: Element): String {
  val outer = wrapper.outerHtml()
  val openEnd = outer.indexOf('>')
  if (openEnd <= 0) return "<${wrapper.tagName()}>"
  return outer.substring(0, openEnd + 1)
}

// --- Word-budget chunking ----------------------------------------------------------------------

private const val SEPARATOR_WORD_COST = 1
private val cjkRegex =
    Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
private val latinWordRegex = Regex("[\\p{L}\\p{N}]+")

private fun buildChunks(sourceTexts: List<String>, chunkWordLimit: Int): List<TranslationChunk> {
  val normalizedWordLimit = chunkWordLimit.coerceAtLeast(1)
  val chunks = mutableListOf<TranslationChunk>()

  var startIndex = 0
  val current = mutableListOf<String>()
  var currentWords = 0

  sourceTexts.forEachIndexed { index, sourceText ->
    val words = estimateWordCount(sourceText)
    val nextWords = currentWords + if (current.isEmpty()) words else words + SEPARATOR_WORD_COST
    val shouldFlush = current.isNotEmpty() && nextWords > normalizedWordLimit

    if (shouldFlush) {
      chunks += TranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
      current.clear()
      currentWords = 0
      startIndex = index
    }

    current += sourceText
    currentWords += if (current.size == 1) words else words + SEPARATOR_WORD_COST
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

  return (cjkMatches + tokenMatches).coerceAtLeast(1)
}

// --- Chunk translation ---------------------------------------------------------------------

private suspend fun translateChunk(
    chunk: TranslationChunk,
    settings: TranslationSettings,
    dispatcher: TranslationDispatcher,
    resultAligner: TranslationResultAligner,
): List<TranslationBlockResult> {
  if (chunk.sourceTexts.all { it.isBlank() }) {
    return List(chunk.sourceTexts.size) { TranslationBlockResult.EmptyResult }
  }

  val payload = chunk.sourceTexts.joinToString(TranslationResultAligner.batchSeparator)
  val translatedLines =
      resultOfSuspend {
            dispatcher
                .translate(
                    TranslationRequest(
                        provider = settings.provider,
                        sourceText = payload,
                        targetLanguageCode = settings.targetLanguageCode,
                        openAiConfig =
                            settings.openAiConfig.takeIf {
                              settings.provider == TranslationProvider.OPENAI_COMPATIBLE
                            },
                    )
                )
                .trim()
          }
          .fold(
              onSuccess = { translated ->
                if (translated.isBlank()) {
                  List(chunk.sourceTexts.size) { "" }
                } else {
                  try {
                    resultAligner.parseChunkTranslation(
                        translated = translated,
                        expectedBlockCount = chunk.sourceTexts.size,
                    )
                  } catch (error: TranslationAlignmentException) {
                    return List(chunk.sourceTexts.size) { TranslationBlockResult.Failure(error) }
                  }
                }
              },
              onFailure = { error ->
                return List(chunk.sourceTexts.size) { TranslationBlockResult.Failure(error) }
              },
          )

  return translatedLines.map { translatedLine ->
    val cleaned = resultAligner.sanitizeTranslatedBlockText(translatedLine)
    if (cleaned.isBlank()) TranslationBlockResult.EmptyResult
    else TranslationBlockResult.Success(cleaned)
  }
}

// --- Response text normalization/alignment ------------------------------------------------

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
          .replace(" ", " ")
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
