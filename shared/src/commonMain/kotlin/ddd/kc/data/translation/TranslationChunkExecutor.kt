package ddd.kc.data.translation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class TranslationChunkExecutor(private val chunkTranslator: TranslationChunkTranslator) {
  suspend fun translate(
      chunks: List<TranslationChunk>,
      settings: TranslationSettings,
      onChunkResult: (startIndex: Int, results: List<TranslationBlockResult>) -> Unit,
  ) {
    if (chunks.isEmpty()) return

    coroutineScope {
      val resultChannel =
          Channel<Pair<Int, List<TranslationBlockResult>>>(capacity = chunks.size.coerceAtLeast(1))
      val semaphore = Semaphore(settings.maxConcurrency)

      chunks.forEach { chunk ->
        launch {
          semaphore.withPermit {
            resultChannel.send(chunk.startIndex to chunkTranslator.translate(chunk, settings))
          }
        }
      }

      repeat(chunks.size) {
        val (startIndex, results) = resultChannel.receive()
        onChunkResult(startIndex, results)
      }
      resultChannel.close()
    }
  }
}
