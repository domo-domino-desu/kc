package ddd.kc.ui.pages.more

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.QueryError
import ddd.kc.data.remote.network.KcSessionStore
import ddd.kc.data.remote.network.PawchiveApi
import ddd.kc.data.remote.network.toQueryError
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.launch

private val log = KcLog.withTag("MoreScreenModel")

data class SessionUiState(
    val loggedIn: Boolean,
    val session: String,
    val operationInProgress: Boolean = false,
    val loginSucceeded: Boolean = false,
    val error: QueryError? = null,
)

class MoreScreenModel(
    private val api: PawchiveApi,
    private val postRepo: PostRepository,
    private val creatorRepo: CreatorRepository,
    private val sessionStore: KcSessionStore,
) :
    StateScreenModel<SessionUiState>(
        SessionUiState(sessionStore.hasSession(), sessionStore.getSession().orEmpty())
    ) {

  fun saveSession(value: String) {
    sessionStore.saveSession(value)
    mutableState.value =
        mutableState.value.copy(
            loggedIn = sessionStore.hasSession(),
            session = sessionStore.getSession().orEmpty(),
            loginSucceeded = false,
            error = null,
        )
  }

  fun login(username: String, password: String) {
    if (mutableState.value.operationInProgress) return
    mutableState.value =
        mutableState.value.copy(
            operationInProgress = true,
            loginSucceeded = false,
            error = null,
        )
    screenModelScope.launch {
      log.i { "账号密码登录 -> 开始" }
      resultOfSuspend { api.login(username, password) }
          .onSuccess {
            postRepo.clearFavoritesCache()
            creatorRepo.clearFavoritesCache()
            log.i { "账号密码登录 -> 成功" }
            mutableState.value =
                mutableState.value.copy(
                    loggedIn = true,
                    session = sessionStore.getSession().orEmpty(),
                    operationInProgress = false,
                    loginSucceeded = true,
                    error = null,
                )
          }
          .onFailure {
            log.w(it) { "账号密码登录 -> 失败" }
            mutableState.value =
                mutableState.value.copy(
                    operationInProgress = false,
                    loginSucceeded = false,
                    error = it.toQueryError(),
                )
          }
    }
  }

  fun consumeLoginSuccess() {
    mutableState.value = mutableState.value.copy(loginSucceeded = false)
  }

  fun clearError() {
    mutableState.value = mutableState.value.copy(error = null)
  }

  fun logout() {
    if (mutableState.value.operationInProgress) return
    mutableState.value =
        mutableState.value.copy(
            operationInProgress = true,
            loginSucceeded = false,
            error = null,
        )
    screenModelScope.launch {
      log.i { "退出登录 -> 开始" }
      resultOfSuspend { api.logout() }
          .onSuccess {
            postRepo.clearFavoritesCache()
            creatorRepo.clearFavoritesCache()
            log.i { "退出登录 -> 成功" }
            mutableState.value = SessionUiState(loggedIn = false, session = "")
          }
          .onFailure {
            log.e(it) { "退出登录 -> 失败" }
            mutableState.value =
                mutableState.value.copy(operationInProgress = false, error = it.toQueryError())
          }
    }
  }
}
