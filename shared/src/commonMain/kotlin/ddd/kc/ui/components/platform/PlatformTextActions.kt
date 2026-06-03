package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable

/** Copies text to the platform clipboard and returns whether it succeeded. */
@Composable expect fun rememberPlatformTextCopier(): (String) -> Boolean
