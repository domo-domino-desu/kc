package ddd.kc.ui.pages.more

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.network.KcApiClient
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

data class LoginState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class LoginScreenModel(
    private val api: KcApiClient,
) : StateScreenModel<LoginState>(LoginState()) {
  private val log = KcLog.withTag("LoginScreenModel")

  fun onUsernameChanged(value: String) {
    mutableState.value = mutableState.value.copy(username = value, error = null)
  }

  fun onPasswordChanged(value: String) {
    mutableState.value = mutableState.value.copy(password = value, error = null)
  }

  fun login(platform: Platform) {
    val state = mutableState.value
    if (state.username.isBlank() || state.password.isBlank()) return
    mutableState.value = state.copy(isLoading = true, error = null)
    screenModelScope.launch {
      log.i { "登录 -> 开始(platform=${platform.name},usernameLength=${state.username.length})" }
      runCatching { api.login(platform, state.username, state.password) }
          .onSuccess {
            val hasSession = api.hasSession(platform)
            log.i { "登录 -> 成功(platform=${platform.name},hasSession=$hasSession)" }
            if (!hasSession) log.w { "登录 -> API成功但session未保存(platform=${platform.name})" }
            mutableState.value = mutableState.value.copy(isLoading = false, success = true)
          }
          .onFailure {
            log.e(it) { "登录 -> 失败(platform=${platform.name})" }
            mutableState.value = mutableState.value.copy(isLoading = false, error = it.message)
          }
    }
  }
}
