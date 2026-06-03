package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable

/** Launches a platform save-file picker. The returned lambda accepts a suggested file name. */
@Composable
expect fun rememberPlatformFileSavePicker(
    mimeType: String,
    onFilePicked: (String?) -> Unit,
): (String) -> Unit
