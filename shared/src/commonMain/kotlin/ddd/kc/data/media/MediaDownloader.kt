package ddd.kc.data.media

import ddd.kc.data.network.AuthRequiredException
import ddd.kc.data.network.PawchiveApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class MediaDownloader(private val client: HttpClient) {
  suspend fun download(url: String): ByteArray {
    val response = client.get(url)
    if (response.status == HttpStatusCode.Unauthorized) throw AuthRequiredException()
    if (!response.status.isSuccess()) {
      throw PawchiveApiException(response.status.value, "HTTP ${response.status.value}")
    }
    return response.body()
  }
}
