package ddd.kc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

@Composable
fun TextPill(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    shape: Shape = CircleShape,
    border: BorderStroke? = null,
) {
  val color =
      if (highlighted) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh
  val contentColor =
      if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
      modifier =
          modifier.then(
              if (onClick == null) Modifier
              else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
          ),
      shape = shape,
      color = color,
      contentColor = contentColor,
      border = border,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      leadingContent?.invoke()
      Text(text, style = MaterialTheme.typography.labelMedium)
    }
  }
}
