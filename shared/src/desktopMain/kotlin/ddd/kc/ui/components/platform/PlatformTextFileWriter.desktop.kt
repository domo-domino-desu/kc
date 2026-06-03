package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformTextFileWriter():
    suspend (PlatformTextFileWriteRequest) -> PlatformTextFileWriteResult {
  return remember { { request -> writeTextFile(request) } }
}

private suspend fun writeTextFile(
    request: PlatformTextFileWriteRequest,
): PlatformTextFileWriteResult =
    withContext(Dispatchers.IO) {
      val targetFile =
          when (val destination = request.destination) {
            is PlatformTextFileDestination.Directory ->
                destination.relativeDirectories
                    .fold(File(destination.path)) { current, segment -> File(current, segment) }
                    .resolve(request.fileName)
            is PlatformTextFileDestination.File -> File(destination.path)
          }

      val parent = targetFile.parentFile
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        return@withContext PlatformTextFileWriteResult.Failure("Cannot create parent directory")
      }

      val writeSuccess = runCatching { targetFile.writeText(request.content) }.isSuccess
      if (writeSuccess) {
        PlatformTextFileWriteResult.Saved(savedPath = targetFile.absolutePath)
      } else {
        PlatformTextFileWriteResult.Failure("Cannot write target file")
      }
    }
