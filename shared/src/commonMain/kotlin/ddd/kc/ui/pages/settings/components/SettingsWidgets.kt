package ddd.kc.ui.pages.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowRightW400Outlined

@Composable
fun SettingsGroup(
    title: String? = null,
    framed: Boolean = true,
    titleHorizontalPadding: Dp = 18.dp,
    containerHorizontalPadding: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!title.isNullOrBlank()) {
      Text(
          text = title,
          modifier = Modifier.padding(horizontal = titleHorizontalPadding),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    if (framed) {
      Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = containerHorizontalPadding),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border =
              BorderStroke(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              ),
      ) {
        Column(content = content)
      }
    } else {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = containerHorizontalPadding),
          content = content,
      )
    }
  }
}

@Composable
fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
) {
  Column {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          modifier = Modifier.size(34.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.secondaryContainer,
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color =
                if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
          imageVector = Icons.KeyboardArrowRightW400Outlined,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (showDivider) {
      HorizontalDivider(
          modifier = Modifier.padding(start = 60.dp),
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
      )
    }
  }
}

@Composable
fun SettingsControlRow(
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    BoxWithConstraints(
        modifier =
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(vertical = 6.dp)
    ) {
      val stacked = maxWidth < 320.dp
      if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SettingsControlLabel(title = title, supportingText = supportingText)
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            control()
          }
        }
      } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          SettingsControlLabel(
              title = title,
              supportingText = supportingText,
              modifier = Modifier.weight(1f),
          )
          Box(contentAlignment = Alignment.CenterEnd) { control() }
        }
      }
    }
  }
}

@Composable
private fun SettingsControlLabel(
    title: String,
    supportingText: String?,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    supportingText
        ?.takeIf { it.isNotBlank() }
        ?.let {
          Text(
              text = it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
  }
}

@Composable
fun SettingsInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  SettingsControlRow(title = label, supportingText = supportingText, modifier = modifier) {
    CompactSettingsTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
    )
  }
}

@Composable
fun SettingsStackedInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  Column(
      modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SettingsControlLabel(title = label, supportingText = supportingText)
    CompactSettingsTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth(),
        constrainWidth = false,
    )
  }
}

@Composable
private fun CompactSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    minLines: Int,
    visualTransformation: VisualTransformation,
    modifier: Modifier = Modifier,
    constrainWidth: Boolean = true,
) {
  val shape = RoundedCornerShape(8.dp)
  val sizeModifier =
      if (constrainWidth) {
        modifier.widthIn(min = 88.dp, max = if (minLines > 1) 180.dp else 160.dp)
      } else {
        modifier.fillMaxWidth()
      }
  Surface(
      modifier =
          sizeModifier
              .heightIn(min = if (minLines > 1) 96.dp else 36.dp)
              .border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                  shape = shape,
              ),
      shape = shape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
  ) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
    )
  }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 52.dp)
              .clickable(enabled = enabled, onClick = onClick)
              .padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingsControlLabel(title = title, supportingText = subtitle, modifier = Modifier.weight(1f))
    Icon(
        imageVector = Icons.KeyboardArrowRightW400Outlined,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
