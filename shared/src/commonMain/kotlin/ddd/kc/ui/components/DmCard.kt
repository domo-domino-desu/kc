package ddd.kc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import ddd.kc.data.model.DM
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.components.icons.rememberServiceIconDefinition
import ddd.kc.ui.components.icons.serviceIconVector
import ddd.kc.ui.components.paging.ContentTranslationState
import ddd.kc.ui.components.paging.TranslationBlockState
import ddd.kc.ui.components.paging.TranslationStatus
import ddd.kc.utils.collapseConsecutiveBlankLines

@Composable
fun DmCard(
    dm: DM,
    modifier: Modifier = Modifier,
    translationState: ContentTranslationState? = null,
    onTranslate: (() -> Unit)? = null,
    onCreatorClick: (() -> Unit)? = null,
) {
  val baseUrl = LocalAppSettings.current.baseUrl()
  val userId = dm.user.orEmpty()
  val service = dm.service.orEmpty()
  val displayName = dm.artist?.name?.takeIf { it.isNotBlank() } ?: userId.ifBlank { "Unknown" }
  val avatarUrl =
      if (service.isNotBlank() && userId.isNotBlank()) "$baseUrl/icons/$service/$userId" else null
  Surface(
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        NetworkImage(
            url = avatarUrl,
            modifier =
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (onCreatorClick != null) Modifier.clickable(onClick = onCreatorClick)
                        else Modifier
                    ),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = displayName,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier =
                  if (onCreatorClick != null) Modifier.clickable(onClick = onCreatorClick)
                  else Modifier,
          )
          if (service.isNotBlank() || !dm.added.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (service.isNotBlank()) DmServiceChip(service)
              if (!dm.added.isNullOrBlank()) DmMetadataChip(dm.added.take(10))
            }
          }
        }
        if (onTranslate != null && !dm.content.isNullOrBlank()) {
          TranslateIconButton(
              onClick = onTranslate,
              isTranslating = translationState?.isTranslating == true,
              isActive = translationState?.showTranslation == true,
          )
        }
      }
      if (translationState?.showTranslation == true && translationState.blocks.isNotEmpty()) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
          translationState.blocks.forEachIndexed { index, block ->
            DmTranslatedBlockItem(
                block = block,
                showDivider = index < translationState.blocks.lastIndex,
            )
          }
        }
      } else {
        SelectionContainer {
          DmHtmlText(
              text =
                  dm.content?.takeIf { it.isNotBlank() }?.let(::collapseConsecutiveBlankLines)
                      ?: "(no content)",
              modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun DmServiceChip(service: String) {
  val serviceIcon = rememberServiceIconDefinition(service)
  DmMetadataChip(
      text = serviceIcon.label,
      icon = serviceIconVector(serviceIcon.icon),
  )
}

@Composable
private fun DmMetadataChip(text: String, icon: ImageVector? = null) {
  Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
      if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp),
        )
      }
      Text(
          text = text,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun DmTranslatedBlockItem(block: TranslationBlockState, showDivider: Boolean) {
  SelectionContainer {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      DmHtmlText(text = collapseConsecutiveBlankLines(block.originalHtml))
      Spacer(Modifier.height(6.dp))
      Text(
          text =
              when (block.status) {
                TranslationStatus.PENDING -> "……"
                TranslationStatus.SUCCESS ->
                    collapseConsecutiveBlankLines(block.translated.orEmpty())
                TranslationStatus.EMPTY -> ""
                TranslationStatus.FAILURE -> "翻译失败"
                TranslationStatus.IDLE -> ""
              },
          style = MaterialTheme.typography.bodySmall,
          color =
              if (block.status == TranslationStatus.FAILURE) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
      )
      if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
      }
    }
  }
}

@Composable
private fun DmHtmlText(text: String, modifier: Modifier = Modifier) {
  val annotatedText = remember(text) { htmlToAnnotatedString(text) }
  Text(
      text = annotatedText,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier.fillMaxWidth(),
  )
}
