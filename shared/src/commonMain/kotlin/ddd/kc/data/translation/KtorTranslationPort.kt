package ddd.kc.data.translation

import io.ktor.client.HttpClient

class KtorTranslationPort(client: HttpClient) : TranslationPort {
  private val transport = KtorTranslationHttpTransport(client)
  private val googleClient = GoogleTranslationClient(transport)
  private val microsoftClient = MicrosoftTranslationClient(transport)
  private val openAiClient = OpenAiCompatibleTranslationClient(transport)

  override suspend fun translate(request: TranslationRequest): String {
    val normalized = request.normalized()
    return when (normalized.provider) {
      TranslationProvider.GOOGLE -> googleClient.translate(normalized)
      TranslationProvider.MICROSOFT -> microsoftClient.translate(normalized)
      TranslationProvider.OPENAI_COMPATIBLE -> openAiClient.translate(normalized)
    }
  }
}
