package ddd.kc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.retry
import kc.shared.generated.resources.search_failed
import kc.shared.generated.resources.search_no_results
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchResultStatus(
    loading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when {
      loading -> CircularProgressIndicator(modifier = Modifier.size(36.dp))
      errorMessage != null -> {
        Text(
            text = stringResource(Res.string.search_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
      }
      else ->
          Text(
              text = stringResource(Res.string.search_no_results),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
    }
  }
}
