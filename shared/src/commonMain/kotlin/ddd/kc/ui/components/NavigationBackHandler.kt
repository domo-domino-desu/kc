package ddd.kc.ui.components

import androidx.compose.runtime.Composable

@Composable expect fun NavigationBackHandler(enabled: Boolean = true, onBack: () -> Unit)
