package ddd.kc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.AttachFileW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined

@Composable
fun PostCard(
    post: Post,
    platform: Platform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val cdnUrl = LocalAppSettings.current.cdnUrl(platform)
  val fileCount = post.attachmentCount ?: post.allFiles().size
  val favCount = post.favoriteCount
  val date = post.published?.take(10)?.takeIf { it.isNotBlank() } ?: post.added?.take(10).orEmpty()
  Surface(
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        NetworkImage(
            url = post.thumbnailUrl(cdnUrl),
            modifier = Modifier.matchParentSize(),
            fallbackContent = { PostTitleCoverFallback(title = post.title) },
            logFailureAsWarning = false,
        )
        if (fileCount > 0 || favCount != null) {
          Row(
              modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              verticalAlignment = Alignment.Top,
          ) {
            if (fileCount > 0) {
              PostCardImageChip(
                  icon = Icons.AttachFileW400Outlined,
                  text = fileCount.toString(),
              )
            }
            if (favCount != null) {
              PostCardImageChip(
                  icon = Icons.FavoriteW400Outlined,
                  text = favCount.toString(),
              )
            }
          }
        }
      }
      Column(
          modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = post.title.ifBlank { "(untitled)" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
          if (date.isNotBlank()) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PostCardImageChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
  Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
      shape = RoundedCornerShape(10.dp),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
      Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(13.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = text,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
