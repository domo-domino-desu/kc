package ddd.kc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.PageInfo
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CloseW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowLeftW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowRightW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.MenuW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.VerticalAlignTopW400Outlined
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.confirm
import kc.shared.generated.resources.jump_to_page
import kc.shared.generated.resources.page_first
import kc.shared.generated.resources.page_last
import kc.shared.generated.resources.page_navigation
import kc.shared.generated.resources.page_next
import kc.shared.generated.resources.page_number
import kc.shared.generated.resources.page_number_with_total
import kc.shared.generated.resources.page_previous
import kc.shared.generated.resources.target_page_number_with_range
import org.jetbrains.compose.resources.stringResource

@Composable
fun PageJumpFabMenu(
    pageInfo: PageInfo?,
    loading: Boolean,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  val info = pageInfo?.takeIf { it.lastPage > 1 } ?: return
  var expanded by rememberSaveable { mutableStateOf(false) }
  var dialogVisible by rememberSaveable { mutableStateOf(false) }
  var pageInput by
      rememberSaveable(info.currentPage, info.lastPage) {
        mutableStateOf(info.currentPage.toString())
      }

  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.End,
  ) {
    PageFabAction(
        visible = expanded,
        icon = Icons.VerticalAlignTopW400Outlined,
        contentDescription = stringResource(Res.string.page_first),
        enabled = info.hasPrevious && !loading,
        onClick = {
          expanded = false
          onJumpToPage(1)
        },
    )
    PageFabAction(
        visible = expanded,
        icon = Icons.KeyboardArrowLeftW400Outlined,
        contentDescription = stringResource(Res.string.page_previous),
        enabled = info.hasPrevious && !loading,
        onClick = {
          expanded = false
          onJumpToPage(info.currentPage - 1)
        },
    )
    PageFabAction(
        visible = expanded,
        label = stringResource(Res.string.page_number, info.currentPage),
        contentDescription =
            stringResource(Res.string.page_number_with_total, info.currentPage, info.lastPage),
        enabled = !loading,
        onClick = {
          pageInput = info.currentPage.toString()
          dialogVisible = true
        },
    )
    PageFabAction(
        visible = expanded,
        icon = Icons.KeyboardArrowRightW400Outlined,
        contentDescription = stringResource(Res.string.page_next),
        enabled = info.hasNext && !loading,
        onClick = {
          expanded = false
          onJumpToPage(info.currentPage + 1)
        },
    )
    PageFabAction(
        visible = expanded,
        icon = Icons.VerticalAlignTopW400Outlined,
        iconModifier = Modifier.rotate(180f),
        contentDescription = stringResource(Res.string.page_last),
        enabled = info.hasNext && !loading,
        onClick = {
          expanded = false
          onJumpToPage(info.lastPage)
        },
    )
    FloatingActionButton(onClick = { expanded = !expanded }) {
      Icon(
          imageVector = if (expanded) Icons.CloseW400Outlined else Icons.MenuW400Outlined,
          contentDescription = stringResource(Res.string.page_navigation),
      )
    }
  }

  if (dialogVisible) {
    val parsedPage = pageInput.toIntOrNull()?.takeIf { it in 1..info.lastPage }
    AlertDialog(
        onDismissRequest = { dialogVisible = false },
        title = { Text(stringResource(Res.string.jump_to_page)) },
        text = {
          OutlinedTextField(
              value = pageInput,
              onValueChange = { pageInput = it.filter(Char::isDigit) },
              label = {
                Text(stringResource(Res.string.target_page_number_with_range, info.lastPage))
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                parsedPage?.let(onJumpToPage)
                dialogVisible = false
                expanded = false
              },
              enabled = parsedPage != null,
          ) {
            Text(stringResource(Res.string.confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { dialogVisible = false }) {
            Text(stringResource(Res.string.cancel))
          }
        },
    )
  }
}

@Composable
private fun PageFabAction(
    visible: Boolean,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    label: String? = null,
) {
  AnimatedVisibility(
      visible = visible,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
  ) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = if (label == null) CircleShape else RoundedCornerShape(20.dp),
        color =
            if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
            if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier =
            Modifier.then(if (label == null) Modifier.size(40.dp) else Modifier).semantics {
              this.contentDescription = contentDescription
            },
    ) {
      Box(
          modifier =
              if (label == null) Modifier
              else Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          contentAlignment = Alignment.Center,
      ) {
        if (icon != null) {
          Icon(imageVector = icon, contentDescription = null, modifier = iconModifier)
        } else if (label != null) {
          Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}
