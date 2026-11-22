package ddd.kc.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: (@Composable () -> Unit)? = null,
    constrainWidth: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
  val shape = RoundedCornerShape(10.dp)
  BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier =
          modifier
              .then(if (constrainWidth) Modifier.widthIn(min = 120.dp, max = 280.dp) else Modifier)
              .heightIn(min = 40.dp)
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceContainerHighest)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
              .padding(horizontal = 12.dp, vertical = 9.dp),
      enabled = enabled,
      readOnly = readOnly,
      singleLine = singleLine,
      minLines = minLines,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      textStyle =
          MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      visualTransformation = visualTransformation,
      decorationBox = { inner ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
              Text(
                  placeholder,
                  maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                  overflow = TextOverflow.Ellipsis,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            inner()
          }
          trailingContent?.invoke()
        }
      },
  )
}
