package ddd.kc.ui.components.forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddd.kc.ui.components.TextPill

/**
 * A read-only field that summarizes the selected tags and opens a wrapping chip picker to edit
 * them. Editing happens entirely inside the dialog so it works with a touch keyboard.
 */
@Composable
fun TagInput(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    dialogTitle: String,
    emptyLabel: String,
    checkIcon: ImageVector,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Confirm",
    cancelLabel: String = "Cancel",
    suggestions: List<String> = emptyList(),
    tagLabel: (String) -> String = { it },
    enabled: Boolean = true,
) {
  var dialogVisible by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(10.dp)

  Box(
      modifier =
          modifier
              .heightIn(min = 40.dp)
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceContainerHighest)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
              .clickable(enabled = enabled, role = Role.Button) { dialogVisible = true }
              .semantics { contentDescription = dialogTitle }
              .padding(horizontal = 12.dp, vertical = 9.dp),
      contentAlignment = Alignment.CenterStart,
  ) {
    if (tags.isEmpty()) {
      Text(
          text = emptyLabel,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    } else {
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        tags.forEach { tag ->
          TextPill(
              text = tagLabel(tag),
              highlighted = true,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
          )
        }
      }
    }
  }

  if (dialogVisible) {
    var staged by remember(tags) { mutableStateOf(tags.toSet()) }
    val options = remember(suggestions, tags) { (suggestions + tags).distinct() }
    AlertDialog(
        onDismissRequest = { dialogVisible = false },
        title = { Text(dialogTitle) },
        text = {
          if (options.isEmpty()) {
            Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
          } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              options.forEach { option ->
                val checked = option in staged
                SelectableTagChip(
                    text = tagLabel(option),
                    checked = checked,
                    checkIcon = checkIcon,
                    onClick = { staged = if (checked) staged - option else staged + option },
                )
              }
            }
          }
        },
        confirmButton = {
          TextButton(
              onClick = {
                onTagsChange(staged.toList())
                dialogVisible = false
              }
          ) {
            Text(confirmLabel)
          }
        },
        dismissButton = { TextButton(onClick = { dialogVisible = false }) { Text(cancelLabel) } },
    )
  }
}

@Composable
private fun SelectableTagChip(
    text: String,
    checked: Boolean,
    checkIcon: ImageVector,
    onClick: () -> Unit,
) {
  Surface(
      onClick = onClick,
      shape = CircleShape,
      color =
          if (checked) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor =
          if (checked) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
          Modifier.semantics {
            role = Role.Switch
            contentDescription = text
            selected = checked
          },
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text, style = MaterialTheme.typography.labelMedium)
      if (checked) {
        Icon(imageVector = checkIcon, contentDescription = null, modifier = Modifier.size(16.dp))
      }
    }
  }
}
