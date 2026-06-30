package ddd.kc.data.network

import ddd.kc.data.model.Platform
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PAWCHIVE_SESSION_KEY = "pawchive_session"

class KcSessionStore(private val vault: KSafe) {
  private val _loggedInPlatforms =
      MutableStateFlow(
          if (vault.getDirect(PAWCHIVE_SESSION_KEY, "").isNotBlank()) setOf(Platform.PAWCHIVE)
          else emptySet()
      )
  val loggedInPlatforms: StateFlow<Set<Platform>> = _loggedInPlatforms

  fun getSession(platform: Platform): String? =
      vault.getDirect(PAWCHIVE_SESSION_KEY, "").takeIf { it.isNotBlank() }

  fun hasSession(platform: Platform): Boolean = getSession(platform) != null

  fun saveSession(platform: Platform, session: String) {
    val normalized = normalizeSession(session)
    if (normalized.isBlank()) {
      clearSession(platform)
      return
    }
    vault.putDirect(PAWCHIVE_SESSION_KEY, normalized)
    _loggedInPlatforms.value = setOf(Platform.PAWCHIVE)
  }

  fun clearSession(platform: Platform) {
    vault.deleteDirect(PAWCHIVE_SESSION_KEY)
    _loggedInPlatforms.value = emptySet()
  }

  fun cookieHeader(platform: Platform = Platform.PAWCHIVE): String {
    val session = getSession(platform) ?: throw AuthRequiredException()
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
