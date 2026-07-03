package ddd.kc.ui.pages.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ExpandMoreW400Outlined

@Composable
fun <T> SettingsDropdownField(
    label: String,
    supportingText: String? = null,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  SettingsControlRow(title = label, supportingText = supportingText, modifier = modifier) {
    Box {
      FilterChip(
          selected = true,
          onClick = { expanded = true },
          label = { Text(optionLabel(selected)) },
          trailingIcon = {
            Icon(
                imageVector = Icons.ExpandMoreW400Outlined,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
          },
      )
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { candidate ->
          DropdownMenuItem(
              text = { Text(optionLabel(candidate)) },
              onClick = {
                onSelect(candidate)
                expanded = false
              },
          )
        }
      }
    }
  }
}
