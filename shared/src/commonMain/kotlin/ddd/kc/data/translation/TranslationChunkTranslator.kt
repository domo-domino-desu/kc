package ddd.kc.data.translation

import ddd.kc.utils.coroutines.resultOfSuspend

internal class TranslationChunkTranslator(
    private val translationPort: TranslationPort,
    private val resultAligner: TranslationResultAligner,
) {
  suspend fun translate(
      chunk: TranslationChunk,
      settings: TranslationSettings,
  ): List<TranslationBlockResult> {
    if (chunk.sourceTexts.all { it.isBlank() }) {
      return List(chunk.sourceTexts.size) { TranslationBlockResult.EmptyResult }
    }

    val payload = chunk.sourceTexts.joinToString(TranslationResultAligner.batchSeparator)
    val translatedLines =
        resultOfSuspend {
              translationPort
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
}
