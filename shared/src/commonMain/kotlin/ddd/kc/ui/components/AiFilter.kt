package ddd.kc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.AiFilterMode
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ExpandMoreW400Outlined
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.ai_filter_label
import kc.shared.generated.resources.filter_hide
import kc.shared.generated.resources.filter_only
import kc.shared.generated.resources.filter_show
import kc.shared.generated.resources.filter_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun CollapsibleFilterPanel(
    chips: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  var expanded by remember { mutableStateOf(true) }
  Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
    if (expanded) {
      Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        content()
        Icon(
            imageVector = Icons.ExpandMoreW400Outlined,
            contentDescription = null,
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .clickable { expanded = false }
                    .padding(4.dp)
                    .rotate(180f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      Row(
          modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(Res.string.filter_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
          chips.forEach { chip ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
              Text(chip, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
fun AiFilterField(value: AiFilterMode, onChange: (AiFilterMode) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val label = aiFilterLabel(value)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(stringResource(Res.string.ai_filter_label))
    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
      FilterChip(
          selected = value != AiFilterMode.SHOW,
          onClick = { expanded = true },
          label = { Text(label) },
      )
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        AiFilterMode.entries.forEach { mode ->
          DropdownMenuItem(
              text = { Text(aiFilterLabel(mode)) },
              onClick = {
                onChange(mode)
                expanded = false
              },
          )
        }
      }
    }
  }
}

@Composable
fun aiFilterLabel(value: AiFilterMode): String =
    when (value) {
      AiFilterMode.SHOW -> stringResource(Res.string.filter_show)
      AiFilterMode.HIDE -> stringResource(Res.string.filter_hide)
      AiFilterMode.ONLY -> stringResource(Res.string.filter_only)
    }
