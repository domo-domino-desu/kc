package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.EventQueue
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberPlatformFileSavePicker(
    mimeType: String,
    onFilePicked: (String?) -> Unit,
): (String) -> Unit {
  val latestCallback = rememberUpdatedState(onFilePicked)
  return remember(mimeType) {
    { suggestedFileName ->
      val selectedPath =
          runCatching {
                runOnAwtEventThread {
                  JFileChooser().let { chooser ->
                    chooser.dialogType = JFileChooser.SAVE_DIALOG
                    chooser.isMultiSelectionEnabled = false
                    chooser.selectedFile = File(suggestedFileName)
                    val result = chooser.showSaveDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                      chooser.selectedFile?.absolutePath
                    } else {
                      null
                    }
                  }
                }
              }
              .getOrNull()
      latestCallback.value(selectedPath)
    }
  }
}

private fun <T> runOnAwtEventThread(block: () -> T): T {
  if (EventQueue.isDispatchThread()) return block()
  var result: Result<T>? = null
  EventQueue.invokeAndWait { result = runCatching(block) }
  return checkNotNull(result).getOrThrow()
}
