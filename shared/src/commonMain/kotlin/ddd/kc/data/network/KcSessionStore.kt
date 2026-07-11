package ddd.kc.data.network

import ddd.kc.data.security.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PAWCHIVE_SESSION_KEY = "pawchive_session"

class KcSessionStore(private val secretStore: SecretStore) {
  private val _loggedIn =
      MutableStateFlow(secretStore.get(PAWCHIVE_SESSION_KEY).orEmpty().isNotBlank())
  val loggedIn: StateFlow<Boolean> = _loggedIn

  fun getSession(): String? = secretStore.get(PAWCHIVE_SESSION_KEY)

  fun hasSession(): Boolean = getSession() != null

  fun saveSession(session: String) {
    val normalized = normalizeSession(session)
    if (normalized.isBlank()) {
      clearSession()
      return
    }
    secretStore.put(PAWCHIVE_SESSION_KEY, normalized)
    _loggedIn.value = true
  }

  fun clearSession() {
    secretStore.delete(PAWCHIVE_SESSION_KEY)
    _loggedIn.value = false
  }

  fun cookieHeader(): String {
    val session = getSession() ?: throw AuthRequiredException()
    return "session=$session"
  }

  private fun normalizeSession(raw: String): String =
      raw.trim()
          .split(';')
          .firstOrNull { it.trim().startsWith("session=") }
          ?.substringAfter("session=")
          ?.trim()
          ?.takeIf { it.isNotBlank() } ?: raw.trim().removePrefix("session=").trim()
}
