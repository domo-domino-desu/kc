package ddd.kc.data.translation

data class TranslationSettings(
    val enabled: Boolean = true,
    val provider: TranslationProvider = TranslationProvider.default,
    val targetLanguageCode: String = "zh-CN",
    val chunkWordLimit: Int = 1024,
    val maxConcurrency: Int = 3,
    val openAiConfig: OpenAiTranslationConfig = OpenAiTranslationConfig(),
)
