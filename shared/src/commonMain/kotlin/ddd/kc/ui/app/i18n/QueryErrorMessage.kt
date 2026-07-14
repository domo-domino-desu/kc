package ddd.kc.ui.app.i18n

import androidx.compose.runtime.Composable
import ddd.kc.data.model.QueryError
import ddd.kc.data.remote.network.toQueryError
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.error_cloudflare_challenge
import kc.shared.generated.resources.error_cloudflare_challenge_cancelled
import kc.shared.generated.resources.error_forbidden
import kc.shared.generated.resources.error_http
import kc.shared.generated.resources.error_network
import kc.shared.generated.resources.error_parse
import kc.shared.generated.resources.error_rate_limited
import kc.shared.generated.resources.error_unauthorized
import kc.shared.generated.resources.error_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
fun QueryError.localizedMessage(): String =
    when (this) {
      is QueryError.Network -> stringResource(Res.string.error_network)
      is QueryError.Unauthorized -> stringResource(Res.string.error_unauthorized)
      is QueryError.CfChallenge -> stringResource(Res.string.error_cloudflare_challenge)
      is QueryError.ChallengeCancelled ->
          stringResource(Res.string.error_cloudflare_challenge_cancelled)
      is QueryError.RateLimited -> stringResource(Res.string.error_rate_limited)
      is QueryError.Forbidden -> stringResource(Res.string.error_forbidden)
      is QueryError.Http -> stringResource(Res.string.error_http, code)
      is QueryError.Parse -> stringResource(Res.string.error_parse)
      is QueryError.Unknown -> stringResource(Res.string.error_unknown)
    }

@Composable fun Throwable.localizedMessage(): String = toQueryError().localizedMessage()
