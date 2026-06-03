package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberPlatformTextCopier(): (String) -> Boolean {
  return remember {
    { text ->
      runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
          }
          .isSuccess
    }
  }
}
