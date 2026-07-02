package ddd.kc.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun NavigationBackHandler(enabled: Boolean, onBack: () -> Unit) {
  BackHandler(enabled = enabled, onBack = onBack)
}
