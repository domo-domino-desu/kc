package ddd.kc.ui.components.platform

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import ddd.kc.utils.logging.KcLog

private val directoryPickerLog = KcLog.withTag("PlatformDirectoryPicker")

@Composable
actual fun rememberPlatformDirectoryPicker(onDirectoryPicked: (String?) -> Unit): () -> Unit {
  val latestCallback = rememberUpdatedState(onDirectoryPicked)
  val context = LocalContext.current
  val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
          latestCallback.value(null)
          return@rememberLauncherForActivityResult
        }
        runCatching {
              val flags =
                  Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
              context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            .onFailure { error -> directoryPickerLog.w(error) { "持久化目录访问权限失败" } }
        latestCallback.value(uri.toString())
      }
  return remember(launcher) { { launcher.launch(null) } }
}
