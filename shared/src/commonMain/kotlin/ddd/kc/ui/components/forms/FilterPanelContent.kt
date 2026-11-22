package ddd.kc.ui.components.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import ddd.kc.ui.components.TextPill

/** Stateless filter presentation. The caller controls expansion or opens its own route. */
@Composable
fun FilterPanelContent(
    chips: List<String>,
    filterIcon: ImageVector,
    collapseIcon: ImageVector,
    openLabel: String,
    closeLabel: String = openLabel,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit = {},
) {
  val drawerVisible = expanded
  Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
    Column {
      if (!drawerVisible) {
        Surface(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = openLabel },
            shape = RoundedCornerShape(14.dp),
        ) {
          Row(
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              chips
                  .filter(String::isNotBlank)
                  .ifEmpty { listOf(openLabel) }
                  .forEach { chip ->
                    TextPill(text = chip, highlighted = true, shape = CircleShape)
                  }
            }
            Icon(
                filterIcon,
                null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      if (drawerVisible) {
        FilterGrid(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 8.dp),
            content = content,
        )
        IconButton(
            onClick = { onExpandedChange(false) },
            modifier = Modifier.align(Alignment.CenterHorizontally).size(48.dp),
        ) {
          Icon(
              collapseIcon,
              closeLabel,
              Modifier.size(24.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
      }
    }
  }
}

@Composable
internal fun FilterFieldRow(label: String, modifier: Modifier, control: @Composable () -> Unit) {
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val maxLabelWidth = maxWidth * 0.45f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
          label,
          modifier = Modifier.widthIn(max = maxLabelWidth),
          style = MaterialTheme.typography.bodyMedium,
      )
      Box(Modifier.weight(1f)) { control() }
    }
  }
}

/** A label/control pair occupying one adaptive grid cell. */
@Composable
fun FilterDropdownField(
    label: String,
    selectedLabel: String,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
  FilterFieldRow(label, modifier) {
    CompactDropdown(
        value = selectedLabel,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        menuContent = menuContent,
    )
  }
}

/** A labelled input row with the same column alignment as [FilterDropdownField]. */
@Composable
fun FilterInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
  FilterFieldRow(label, modifier) {
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        singleLine = true,
        placeholder = placeholder,
        constrainWidth = false,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingContent = trailingIcon,
    )
  }
}

/**
 * Non-lazy grid suitable for drawers inside scrolling lists; composed helpers emit individual
 * cells.
 */
@Composable
private fun FilterGrid(modifier: Modifier, content: @Composable () -> Unit) {
  Layout(content = content, modifier = modifier) { measurables, constraints ->
    val gap = 8.dp.roundToPx()
    val minimum = 120.dp.roundToPx()
    val width =
        if (constraints.hasBoundedWidth) constraints.maxWidth
        else constraints.constrainWidth(minimum)
    val columns =
        ((width + gap) / (minimum + gap))
            .coerceAtLeast(1)
            .coerceAtMost(measurables.size.coerceAtLeast(1))
    val available = (width - gap * (columns - 1)).coerceAtLeast(0)
    val cellWidths =
        IntArray(columns) { available / columns + if (it < available % columns) 1 else 0 }
    val placeables =
        measurables.mapIndexed { index, measurable ->
          measurable.measure(Constraints.fixedWidth(cellWidths[index % columns]))
        }
    val rows = placeables.chunked(columns)
    val rowHeights = rows.map { row -> row.maxOf { it.height } }
    val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
    layout(width, constraints.constrainHeight(height)) {
      var y = 0
      rows.forEachIndexed { rowIndex, row ->
        var x = 0
        row.forEachIndexed { column, placeable ->
          placeable.placeRelative(x, y + (rowHeights[rowIndex] - placeable.height) / 2)
          x += cellWidths[column] + gap
        }
        y += rowHeights[rowIndex] + gap
      }
    }
  }
}
