package ddd.kc.ui.components.forms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The compact selected-chip dropdown used by app settings and filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactDropdown(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Box {
    FilterChip(
        selected = true,
        onClick = { expanded = true },
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
        label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded, Modifier.size(18.dp)) },
    )
    DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }) {
      menuContent { expanded = false }
    }
  }
}
