package ddd.kc.data.translation

internal interface TranslationProviderClient {
  suspend fun translate(request: TranslationRequest): String
}

internal fun ensureTranslationSuccess(statusCode: Int, provider: String) {
  if (statusCode in 200..299) return
  throw TranslationProviderException(provider = provider, statusCode = statusCode)
}

internal class TranslationProviderException(provider: String, val statusCode: Int) :
    IllegalStateException("$provider translation failed: status=$statusCode")

internal fun ensureTranslatedNonBlank(provider: String, translated: String): String {
  if (translated.isBlank()) throw IllegalStateException("$provider translation result is blank")
  return translated
}
