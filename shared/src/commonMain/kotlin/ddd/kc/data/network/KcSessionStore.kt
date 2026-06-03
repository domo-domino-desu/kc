package ddd.kc.data.network

import ddd.kc.data.model.Platform
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val KEMONO_SESSION_KEY = "kemono_session"
private const val COOMER_SESSION_KEY = "coomer_session"

class KcSessionStore(private val vault: KSafe) {
  private val _loggedInPlatforms =
      MutableStateFlow(
          Platform.entries.filter { vault.getDirect(it.sessionKey(), "").isNotBlank() }.toSet()
      )
  val loggedInPlatforms: StateFlow<Set<Platform>> = _loggedInPlatforms

  fun getSession(platform: Platform): String? =
      vault.getDirect(platform.sessionKey(), "").takeIf { it.isNotBlank() }

  fun hasSession(platform: Platform): Boolean = getSession(platform) != null

  fun saveSession(platform: Platform, session: String) {
    vault.putDirect(platform.sessionKey(), session)
    _loggedInPlatforms.value = _loggedInPlatforms.value + platform
  }

  fun clearSession(platform: Platform) {
    vault.deleteDirect(platform.sessionKey())
    _loggedInPlatforms.value = _loggedInPlatforms.value - platform
  }

  private fun Platform.sessionKey(): String =
      when (this) {
        Platform.KEMONO -> KEMONO_SESSION_KEY
        Platform.COOMER -> COOMER_SESSION_KEY
      }
}
