package ddd.kc.application.translation

import ddd.kc.data.translation.OpenAiTranslationConfig
import ddd.kc.data.translation.TranslationProvider

data class TranslationSettings(
    val provider: TranslationProvider = TranslationProvider.default,
    val targetLanguageCode: String = "zh-CN",
    val chunkWordLimit: Int = 1024,
    val maxConcurrency: Int = 3,
    val openAiConfig: OpenAiTranslationConfig = OpenAiTranslationConfig(),
)
