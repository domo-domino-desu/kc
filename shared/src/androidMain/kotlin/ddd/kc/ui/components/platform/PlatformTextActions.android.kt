package ddd.kc.ui.components.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformTextCopier(): (String) -> Boolean {
  val context = LocalContext.current
  return remember(context) {
    { text ->
      runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("kc-text", text))
          }
          .isSuccess
    }
  }
}
