package ddd.kc.data.translation

internal interface TranslationProviderClient {
  suspend fun translate(request: TranslationRequest): String
}

internal fun ensureTranslationSuccess(statusCode: Int, responseBody: String, provider: String) {
  if (statusCode in 200..299) return
  throw IllegalStateException(
      "$provider translation failed: $statusCode body=${responseBody.take(160)}"
  )
}

internal fun ensureTranslatedNonBlank(provider: String, translated: String): String {
  if (translated.isBlank()) throw IllegalStateException("$provider translation result is blank")
  return translated
}
