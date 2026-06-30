package ddd.kc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.bannerUrl
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.ui.icons.rememberServiceIconDefinition
import ddd.kc.ui.icons.serviceIconVector

@Composable
fun CreatorSearchCard(
    creator: Creator,
    platform: Platform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFavoriteCount: Boolean = true,
) {
  val baseUrl = LocalAppSettings.current.baseUrl(platform)
  val bannerUrl = creator.bannerUrl(baseUrl)
  Surface(
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
      NetworkImage(
          url = bannerUrl,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
      )
      Box(
          modifier =
              Modifier.matchParentSize()
                  .background(
                      Brush.verticalGradient(
                          listOf(Color.Black.copy(alpha = 0.48f), Color.Black.copy(alpha = 0.82f))
                      )
                  )
      )
      Row(
          modifier = Modifier.fillMaxSize().padding(14.dp),
          verticalAlignment = Alignment.Bottom,
      ) {
        Surface(
            shape = CircleShape,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
            color = MaterialTheme.colorScheme.surface,
        ) {
          NetworkImage(
              url = creator.thumbnailUrl(baseUrl),
              modifier = Modifier.size(64.dp).clip(CircleShape),
              contentScale = ContentScale.Crop,
          )
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
              text = creator.name.ifBlank { creator.id },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreatorPill(service = creator.service)
            if (showFavoriteCount) {
              CreatorFavoritePill(count = creator.favorited)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CreatorPill(service: String) {
  val serviceIcon = rememberServiceIconDefinition(service)
  val imageVector = serviceIconVector(serviceIcon.icon)
  Surface(
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
      shape = RoundedCornerShape(999.dp),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
      if (imageVector != null) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(13.dp),
        )
      }
      Text(
          text = serviceIcon.label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun CreatorFavoritePill(count: Int) {
  Surface(
      color = Color.Black.copy(alpha = 0.58f),
      shape = RoundedCornerShape(999.dp),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
      Icon(
          imageVector = Icons.FavoriteW400Outlined,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(14.dp),
      )
      Text(
          text = count.toString(),
          style = MaterialTheme.typography.labelMedium,
          color = Color.White,
          maxLines = 1,
      )
    }
  }
}
