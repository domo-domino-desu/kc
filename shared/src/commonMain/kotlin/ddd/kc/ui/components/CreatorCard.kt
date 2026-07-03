package ddd.kc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.ui.app.LocalAppSettings

@Composable
fun CreatorCard(
    creator: Creator,
    platform: Platform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val baseUrl = LocalAppSettings.current.baseUrl(platform)
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    NetworkImage(
        url = creator.thumbnailUrl(baseUrl),
        modifier = Modifier.size(48.dp).clip(CircleShape),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
        text = creator.name,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = creator.service,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
