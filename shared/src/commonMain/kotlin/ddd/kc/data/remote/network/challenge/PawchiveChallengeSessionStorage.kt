package ddd.kc.data.remote.network.challenge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PawchiveChallengeSessionStorage {
  private val mutex = Mutex()
  private var active: ActivePawchiveChallengeSession? = null
  private val mutableState =
      MutableStateFlow<PawchiveChallengeUiState>(PawchiveChallengeUiState.Idle)
  val state: StateFlow<PawchiveChallengeUiState> = mutableState.asStateFlow()

  suspend fun acquire(challenge: PawchiveChallengeSignal): ChallengeSessionAcquisition =
      mutex.withLock {
        active?.let {
          return@withLock ChallengeSessionAcquisition(it, created = false)
        }
        val created = ActivePawchiveChallengeSession(challenge, CompletableDeferred())
        active = created
        mutableState.value =
            PawchiveChallengeUiState.Active(
                challenge = challenge,
                status = PawchiveChallengeStatus.AwaitingUserAction,
            )
        ChallengeSessionAcquisition(created, created = true)
      }

  suspend fun current(): ActivePawchiveChallengeSession? = mutex.withLock { active }

  suspend fun markVerifying(session: ActivePawchiveChallengeSession) {
    update(session, PawchiveChallengeStatus.Verifying)
  }

  suspend fun markVerificationFailed(session: ActivePawchiveChallengeSession, detail: String?) {
    update(session, PawchiveChallengeStatus.VerificationFailed(detail))
  }

  suspend fun complete(result: Boolean): ActivePawchiveChallengeSession? =
      mutex.withLock {
        val session = active ?: return@withLock null
        active = null
        mutableState.value = PawchiveChallengeUiState.Idle
        session.deferred.complete(result)
        session
      }

  private suspend fun update(
      session: ActivePawchiveChallengeSession,
      status: PawchiveChallengeStatus,
  ) {
    mutex.withLock {
      if (active !== session) return
      mutableState.value = PawchiveChallengeUiState.Active(session.challenge, status)
    }
  }
}

data class ActivePawchiveChallengeSession(
    val challenge: PawchiveChallengeSignal,
    val deferred: CompletableDeferred<Boolean>,
)

data class ChallengeSessionAcquisition(
    val session: ActivePawchiveChallengeSession,
    val created: Boolean,
)
