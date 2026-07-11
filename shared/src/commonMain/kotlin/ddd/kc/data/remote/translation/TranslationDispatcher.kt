package ddd.kc.data.remote.translation

import ddd.kc.data.model.TranslationProvider
import io.ktor.client.HttpClient

/** Routes a translation request to the configured provider's client. */
class TranslationDispatcher(client: HttpClient) {
  private val googleClient = GoogleTranslationClient(client)
  private val microsoftClient = MicrosoftTranslationClient(client)
  private val openAiClient = OpenAiCompatibleTranslationClient(client)

  suspend fun translate(request: TranslationRequest): String {
    val normalized = request.normalized()
    return when (normalized.provider) {
      TranslationProvider.GOOGLE -> googleClient.translate(normalized)
      TranslationProvider.MICROSOFT -> microsoftClient.translate(normalized)
      TranslationProvider.OPENAI_COMPATIBLE -> openAiClient.translate(normalized)
    }
  }
}

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
