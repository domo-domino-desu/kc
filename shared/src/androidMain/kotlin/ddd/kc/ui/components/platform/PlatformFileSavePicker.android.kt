package ddd.kc.ui.components.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberPlatformFileSavePicker(
    mimeType: String,
    onFilePicked: (String?) -> Unit,
): (String) -> Unit {
  val latestCallback = rememberUpdatedState(onFilePicked)
  val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        latestCallback.value(uri?.toString())
      }
  return remember(launcher) { { suggestedFileName -> launcher.launch(suggestedFileName) } }
}
