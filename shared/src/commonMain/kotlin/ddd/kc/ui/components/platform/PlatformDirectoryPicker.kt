package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable

/** Remembers a platform directory picker trigger. */
@Composable
expect fun rememberPlatformDirectoryPicker(onDirectoryPicked: (String?) -> Unit): () -> Unit
