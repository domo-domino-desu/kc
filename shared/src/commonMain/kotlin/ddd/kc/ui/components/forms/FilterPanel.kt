package ddd.kc.ui.components.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Default drawer: summaries and controls are derived from the same field definitions. */
@Composable
fun FilterPanel(
    fields: List<FilterField>,
    filterIcon: ImageVector,
    collapseIcon: ImageVector,
    openLabel: String,
    closeLabel: String = openLabel,
    modifier: Modifier = Modifier,
    summarySeparator: String = "：",
    onOpen: (() -> Unit)? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  FilterPanelContent(
      chips = fields.map { it.label + summarySeparator + it.summary },
      filterIcon = filterIcon,
      collapseIcon = collapseIcon,
      openLabel = openLabel,
      closeLabel = closeLabel,
      modifier = modifier,
      expanded = expanded && onOpen == null,
      onExpandedChange = { if (it && onOpen != null) onOpen() else expanded = it },
  ) {
    fields.forEach { field ->
      key(field.key) {
        when (field) {
          is FilterField.Dropdown<*> -> Dropdown(field)
          is FilterField.MultiSelect<*> -> MultiSelect(field)
          is FilterField.Input -> Input(field)
          is FilterField.Tags -> Tags(field)
        }
      }
    }
  }
}

@Composable
private fun Tags(field: FilterField.Tags) {
  FilterFieldRow(label = field.label, modifier = Modifier) {
    TagInput(
        tags = field.value,
        onTagsChange = field.onChange,
        dialogTitle = field.label,
        emptyLabel = field.emptyLabel,
        checkIcon = field.checkIcon,
        confirmLabel = field.confirmLabel,
        cancelLabel = field.cancelLabel,
        suggestions = field.suggestions,
        tagLabel = field.tagLabel,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun <T> Dropdown(field: FilterField.Dropdown<T>) {
  FilterDropdownField(field.label, field.summary) { dismiss ->
    field.options.forEach { option ->
      DropdownMenuItem(
          text = { Text(option.label) },
          onClick = {
            dismiss()
            field.onChange(option.value)
          },
      )
    }
  }
}

@Composable
private fun <T> MultiSelect(field: FilterField.MultiSelect<T>) {
  FilterDropdownField(field.label, field.summary) { dismiss ->
    DropdownMenuItem(
        text = { Text(field.emptyLabel) },
        onClick = {
          dismiss()
          field.onChange(emptySet())
        },
    )
    field.options.forEach { option ->
      DropdownMenuItem(
          text = { Text(option.label) },
          trailingIcon = {
            Checkbox(checked = option.value in field.value, onCheckedChange = null)
          },
          onClick = {
            field.onChange(
                if (option.value in field.value) field.value - option.value
                else field.value + option.value
            )
          },
      )
    }
  }
}

@Composable
private fun Input(field: FilterField.Input) {
  val submit = field.onSubmit
  FilterInputField(
      label = field.label,
      value = field.value,
      onValueChange = field.onChange,
      placeholder = field.placeholder,
      keyboardOptions =
          KeyboardOptions(imeAction = if (submit == null) ImeAction.Default else ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { if (field.submitEnabled) submit?.invoke() }),
      trailingIcon =
          if (submit != null && field.submitIcon != null) {
            {
              IconButton(
                  onClick = submit,
                  enabled = field.submitEnabled,
                  modifier = Modifier.size(24.dp),
              ) {
                Icon(field.submitIcon, field.label)
              }
            }
          } else null,
  )
}
