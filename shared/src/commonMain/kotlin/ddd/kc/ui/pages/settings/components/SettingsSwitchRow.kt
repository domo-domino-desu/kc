package ddd.kc.ui.pages.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
) {
  SettingsControlRow(
      title = label,
      supportingText = supportingText,
      modifier = Modifier.clickable { onCheckedChange(!checked) },
  ) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
