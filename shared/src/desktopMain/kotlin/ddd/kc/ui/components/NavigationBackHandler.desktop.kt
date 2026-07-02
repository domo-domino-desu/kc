@file:Suppress("DEPRECATION")

package ddd.kc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

@Composable
@OptIn(ExperimentalComposeUiApi::class)
actual fun NavigationBackHandler(enabled: Boolean, onBack: () -> Unit) {
  BackHandler(enabled = enabled, onBack = onBack)
}
