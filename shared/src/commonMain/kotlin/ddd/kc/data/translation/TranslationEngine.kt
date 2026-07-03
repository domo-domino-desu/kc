package ddd.kc.data.translation

import ddd.kc.data.settings.AppSettings

class TranslationEngine(
    private val translationPort: TranslationPort,
    private val appSettings: AppSettings,
) {
  private val resultAligner = TranslationResultAligner()
  private val blockExtractor = TranslationBlockExtractor(resultAligner)
  private val chunkPlanner = TranslationChunkPlanner()
  private val chunkExecutor =
      TranslationChunkExecutor(
          TranslationChunkTranslator(
              translationPort = translationPort,
              resultAligner = resultAligner,
          )
      )

  fun extractBlocks(html: String): List<TranslationBlock> = blockExtractor.extract(html)

  fun isEnabled(): Boolean = appSettings.translationSettings().enabled

  suspend fun translateBlocks(
      blocks: List<TranslationBlock>,
      onBlockResult: (index: Int, result: TranslationBlockResult) -> Unit,
  ) {
    if (blocks.isEmpty()) return
    val settings = appSettings.translationSettings()
    if (!settings.enabled) return
    val chunks =
        chunkPlanner.buildChunks(
            sourceTexts = blocks.map { it.sourceText },
            chunkWordLimit = settings.chunkWordLimit,
        )
    chunkExecutor.translate(chunks = chunks, settings = settings) { startIndex, results ->
      results.forEachIndexed { offset, result -> onBlockResult(startIndex + offset, result) }
    }
  }
}
