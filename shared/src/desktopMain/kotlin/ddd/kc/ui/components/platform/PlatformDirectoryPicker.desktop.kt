package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.EventQueue
import javax.swing.JFileChooser

@Composable
actual fun rememberPlatformDirectoryPicker(onDirectoryPicked: (String?) -> Unit): () -> Unit {
  val latestCallback = rememberUpdatedState(onDirectoryPicked)
  return remember {
    {
      val selectedPath =
          runCatching {
                runOnAwtEventThread {
                  JFileChooser().let { chooser ->
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    chooser.isMultiSelectionEnabled = false
                    chooser.isAcceptAllFileFilterUsed = false
                    chooser.dialogTitle = "Choose save path"
                    val result = chooser.showOpenDialog(null)
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
