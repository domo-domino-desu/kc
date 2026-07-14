package ddd.kc.data.remote.network.challenge

import io.ktor.http.URLProtocol
import io.ktor.http.Url

object PawchiveOriginPolicy {
  fun normalizedSiteOrigin(raw: String): String? = parseHttpsOrigin(raw)?.origin()

  fun mediaOrigin(siteOrigin: String): String? {
    val parsed = parseHttpsOrigin(siteOrigin) ?: return null
    val host = if (parsed.host.startsWith("img.")) parsed.host else "img.${parsed.host}"
    val portSuffix =
        if (parsed.specifiedPort == 0 || parsed.specifiedPort == parsed.protocol.defaultPort) ""
        else ":${parsed.specifiedPort}"
    return "${parsed.protocol.name}://$host$portSuffix"
  }

  fun isTrustedRequest(requestUrl: String, siteOrigin: String): Boolean {
    val requestOrigin = parseHttpsOrigin(requestUrl)?.origin() ?: return false
    val normalizedSite = normalizedSiteOrigin(siteOrigin) ?: return false
    return requestOrigin == normalizedSite || requestOrigin == mediaOrigin(normalizedSite)
  }

  private fun parseHttpsOrigin(raw: String): Url? {
    val parsed = runCatching { Url(raw.trim()) }.getOrNull() ?: return null
    return parsed.takeIf {
      it.protocol == URLProtocol.HTTPS &&
          it.host.isNotBlank() &&
          it.user.isNullOrBlank() &&
          it.password.isNullOrBlank()
    }
  }

  private fun Url.origin(): String {
    val renderedHost = if (host.contains(':')) "[$host]" else host
    val portSuffix =
        if (specifiedPort == 0 || specifiedPort == protocol.defaultPort) "" else ":$specifiedPort"
    return "${protocol.name}://$renderedHost$portSuffix"
  }
}
