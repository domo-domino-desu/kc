package ddd.kc.ui.app.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ddd.kc.data.remote.network.challenge.PawchiveChallengeController
import ddd.kc.data.remote.network.challenge.PawchiveChallengeStatus
import ddd.kc.data.remote.network.challenge.PawchiveChallengeUiState
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.challenge_awaiting_user_action
import kc.shared.generated.resources.challenge_done
import kc.shared.generated.resources.challenge_failed
import kc.shared.generated.resources.challenge_validating
import kc.shared.generated.resources.challenge_verifying
import kc.shared.generated.resources.reload
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PawchiveChallengeOverlayHost(modifier: Modifier = Modifier) {
  val controller = koinInject<PawchiveChallengeController>()
  val state by controller.state.collectAsState()
  val active = state as? PawchiveChallengeUiState.Active ?: return
  PawchiveChallengeOverlay(active, controller, modifier)
}

@Composable
private fun PawchiveChallengeOverlay(
    state: PawchiveChallengeUiState.Active,
    controller: PawchiveChallengeController,
    modifier: Modifier,
) {
  val challenge = state.challenge
  val adapter = rememberSessionWebViewAdapter(challenge.requestUrl)
  val scope = rememberCoroutineScope()

  LaunchedEffect(challenge) { controller.prepareWebViewSession(adapter.port, challenge) }
  LaunchedEffect(adapter.port.lastLoadedUrl) { controller.syncUserAgentFromWebView(adapter.port) }

  Surface(
      modifier = modifier.fillMaxSize().testTag("cf-challenge-overlay"),
      color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(onClick = { adapter.port.loadUrl(challenge.requestUrl) }) {
          Text(stringResource(Res.string.reload))
        }
        TextButton(onClick = { scope.launch { controller.cancel() } }) {
          Text(stringResource(Res.string.cancel))
        }
        Button(
            enabled = state.status !is PawchiveChallengeStatus.Verifying,
            onClick = { scope.launch { controller.confirmFromWebView(adapter.port) } },
        ) {
          Text(
              if (state.status is PawchiveChallengeStatus.Verifying) {
                stringResource(Res.string.challenge_verifying)
              } else {
                stringResource(Res.string.challenge_done)
              }
          )
        }
      }
      Text(
          text = challengeStatusMessage(state),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 12.dp),
      )
      SessionWebView(
          adapter = adapter,
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp),
      )
    }
  }
}

@Composable
private fun challengeStatusMessage(state: PawchiveChallengeUiState.Active): String =
    when (val status = state.status) {
      PawchiveChallengeStatus.AwaitingUserAction ->
          stringResource(
              Res.string.challenge_awaiting_user_action,
              state.challenge.cfRay?.let { "\nCF-Ray: $it" }.orEmpty(),
          )
      PawchiveChallengeStatus.Verifying -> stringResource(Res.string.challenge_validating)
      is PawchiveChallengeStatus.VerificationFailed ->
          stringResource(Res.string.challenge_failed, status.detail.orEmpty())
    }
