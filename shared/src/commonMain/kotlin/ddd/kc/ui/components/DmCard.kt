package ddd.kc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.TranslationBlockState
import ddd.kc.ui.state.TranslationStatus
import ddd.kc.util.collapseConsecutiveBlankLines

private val urlRegex = Regex("""https?://[^\s<>"']+""")

@Composable
fun DmCard(
    dm: DM,
    platform: Platform,
    modifier: Modifier = Modifier,
    translationState: ContentTranslationState? = null,
    onTranslate: (() -> Unit)? = null,
) {
  val baseUrl = LocalAppSettings.current.baseUrl(platform)
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
      Row(verticalAlignment = Alignment.CenterVertically) {
        NetworkImage(
            url = avatarUrl,
            modifier = Modifier.size(36.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onTranslate != null && !dm.content.isNullOrBlank()) {
          TranslateIconButton(
              onClick = onTranslate,
              isTranslating = translationState?.isTranslating == true,
              isActive = translationState?.showTranslation == true,
          )
        }
        if (!dm.added.isNullOrBlank()) {
          Spacer(Modifier.width(8.dp))
          Text(
              text = dm.added.take(10),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          LinkifiedDmText(
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
private fun DmTranslatedBlockItem(block: TranslationBlockState, showDivider: Boolean) {
  SelectionContainer {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      LinkifiedDmText(text = collapseConsecutiveBlankLines(block.originalHtml))
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
private fun LinkifiedDmText(text: String, modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current
  val parts = remember(text) { splitUrlParts(text) }
  Column(modifier = modifier.fillMaxWidth()) {
    parts.forEach { part ->
      Text(
          text = part.value,
          style = MaterialTheme.typography.bodySmall,
          color =
              if (part.isUrl) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier =
              if (part.isUrl) Modifier.clickable { uriHandler.openUri(part.value) } else Modifier,
      )
    }
  }
}

private data class UrlPart(val value: String, val isUrl: Boolean)

private fun splitUrlParts(text: String): List<UrlPart> {
  val collapsedText = collapseConsecutiveBlankLines(text)
  val parts = mutableListOf<UrlPart>()
  var cursor = 0
  urlRegex.findAll(collapsedText).forEach { match ->
    if (match.range.first > cursor)
        parts += UrlPart(collapsedText.substring(cursor, match.range.first), false)
    parts += UrlPart(match.value, true)
    cursor = match.range.last + 1
  }
  if (cursor < collapsedText.length) parts += UrlPart(collapsedText.substring(cursor), false)
  return parts
}
