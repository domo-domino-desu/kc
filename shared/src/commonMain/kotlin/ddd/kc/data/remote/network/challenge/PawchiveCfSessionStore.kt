package ddd.kc.data.remote.network.challenge

import ddd.kc.data.local.security.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val PAWCHIVE_CF_SESSIONS_KEY = "pawchive_cf_sessions"

@Serializable
data class PawchiveCfSession(
    val cookieHeader: String = "",
    val userAgent: String = DEFAULT_BROWSER_USER_AGENT,
)

class PawchiveCfSessionStore(
    private val secretStore: SecretStore,
    private val json: Json,
) {
  private val mutex = Mutex()
  private var restored = false
  private var sessions = emptyMap<String, PawchiveCfSession>()

  suspend fun load(siteOrigin: String): PawchiveCfSession =
      mutex.withLock {
        restoreLocked()
        sessions[normalizedKey(siteOrigin)] ?: PawchiveCfSession()
      }

  suspend fun save(siteOrigin: String, session: PawchiveCfSession) {
    mutex.withLock {
      restoreLocked()
      val key = normalizedKey(siteOrigin)
      val normalized =
          session.copy(
              cookieHeader = normalizeCloudflareCookieHeader(session.cookieHeader),
              userAgent = session.userAgent.trim().ifBlank { DEFAULT_BROWSER_USER_AGENT },
          )
      sessions = sessions + (key to normalized)
      persistLocked()
    }
  }

  suspend fun merge(
      siteOrigin: String,
      rawCookieHeader: String,
      userAgent: String? = null,
  ): PawchiveCfSession =
      mutex.withLock {
        restoreLocked()
        val key = normalizedKey(siteOrigin)
        val existing = sessions[key] ?: PawchiveCfSession()
        val cookies = parseCookieHeader(existing.cookieHeader).toMutableMap()
        parseCookieHeader(rawCookieHeader).forEach { (name, value) ->
          if (isCloudflareCookieName(name)) cookies[name] = value
        }
        val updated =
            PawchiveCfSession(
                cookieHeader =
                    cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" },
                userAgent = userAgent?.trim()?.takeIf(String::isNotBlank) ?: existing.userAgent,
            )
        sessions = sessions + (key to updated)
        persistLocked()
        updated
      }

  suspend fun mergeSetCookieHeaders(siteOrigin: String, values: List<String>) {
    if (values.isEmpty()) return
    mutex.withLock {
      restoreLocked()
      val key = normalizedKey(siteOrigin)
      val existing = sessions[key] ?: PawchiveCfSession()
      val cookies = parseCookieHeader(existing.cookieHeader).toMutableMap()
      values.forEach { setCookie ->
        val pair = setCookie.substringBefore(';').trim()
        if (!pair.contains('=')) return@forEach
        val name = pair.substringBefore('=').trim()
        if (!isCloudflareCookieName(name)) return@forEach
        val lowered = setCookie.lowercase()
        if ("max-age=0" in lowered || "expires=thu, 01 jan 1970" in lowered) {
          cookies.remove(name)
        } else {
          cookies[name] = pair.substringAfter('=', "").trim()
        }
      }
      sessions =
          sessions +
              (key to
                  existing.copy(
                      cookieHeader =
                          cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
                  ))
      persistLocked()
    }
  }

  private fun restoreLocked() {
    if (restored) return
    sessions =
        secretStore
            .get(PAWCHIVE_CF_SESSIONS_KEY)
            ?.let { raw ->
              runCatching { json.decodeFromString<Map<String, PawchiveCfSession>>(raw) }.getOrNull()
            }
            .orEmpty()
    restored = true
  }

  private fun persistLocked() {
    if (sessions.isEmpty()) secretStore.delete(PAWCHIVE_CF_SESSIONS_KEY)
    else secretStore.put(PAWCHIVE_CF_SESSIONS_KEY, json.encodeToString(sessions))
  }

  private fun normalizedKey(siteOrigin: String): String =
      requireNotNull(PawchiveOriginPolicy.normalizedSiteOrigin(siteOrigin)) {
        "Invalid Pawchive site origin"
      }
}

fun isCloudflareCookieName(name: String): Boolean {
  val normalized = name.trim().lowercase()
  return normalized == "cf_clearance" ||
      normalized.startsWith("cf_") ||
      normalized.startsWith("__cf")
}

fun parseCookieHeader(raw: String): LinkedHashMap<String, String> {
  val result = linkedMapOf<String, String>()
  raw.split(';').forEach { token ->
    val pair = token.trim()
    if (!pair.contains('=')) return@forEach
    val name = pair.substringBefore('=').trim()
    if (name.isNotBlank()) result[name] = pair.substringAfter('=', "").trim()
  }
  return result
}

fun normalizeCloudflareCookieHeader(raw: String): String =
    parseCookieHeader(raw).filterKeys(::isCloudflareCookieName).entries.joinToString("; ") {
        (name, value) ->
      "$name=$value"
    }

fun combineCookieHeaders(vararg headers: String): String =
    headers
        .flatMap { parseCookieHeader(it).entries }
        .associate { it.toPair() }
        .entries
        .joinToString("; ") { (name, value) -> "$name=$value" }

const val DEFAULT_BROWSER_USER_AGENT: String =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
