package ddd.kc.application.translation

import ddd.kc.data.settings.AppSettings
import ddd.kc.domain.translation.TranslationBlock
import ddd.kc.domain.translation.TranslationBlockExtractor
import ddd.kc.domain.translation.TranslationBlockResult
import ddd.kc.domain.translation.TranslationChunkPlanner
import ddd.kc.domain.translation.TranslationPort
import ddd.kc.domain.translation.TranslationResultAligner

class TranslationService(
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

  suspend fun translateBlocks(
      blocks: List<TranslationBlock>,
      onBlockResult: (index: Int, result: TranslationBlockResult) -> Unit,
  ) {
    if (blocks.isEmpty()) return
    val settings = appSettings.translationSettings()
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
