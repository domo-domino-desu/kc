package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformBinaryFileWriter():
    suspend (PlatformBinaryFileWriteRequest) -> PlatformBinaryFileWriteResult {
  return remember { { request -> writeBinaryFile(request) } }
}

private suspend fun writeBinaryFile(
    request: PlatformBinaryFileWriteRequest,
): PlatformBinaryFileWriteResult =
    withContext(Dispatchers.IO) {
      val targetFile =
          when (val destination = request.destination) {
            is PlatformBinaryFileDestination.Directory -> {
              val rootDirectory = File(destination.path)
              runCatching { syncNoMediaFlag(rootDirectory, destination.allowMediaIndexing) }
                  .onFailure {
                    return@withContext PlatformBinaryFileWriteResult.Failure(
                        it.message ?: "Cannot sync .nomedia"
                    )
                  }
              destination.relativeDirectories
                  .fold(rootDirectory) { current, segment -> File(current, segment) }
                  .resolve(request.fileName)
            }
            is PlatformBinaryFileDestination.File -> File(destination.path)
          }

      val parent = targetFile.parentFile
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        return@withContext PlatformBinaryFileWriteResult.Failure("Cannot create parent directory")
      }

      val writeSuccess = runCatching { targetFile.writeBytes(request.bytes) }.isSuccess
      if (writeSuccess) {
        PlatformBinaryFileWriteResult.Saved(savedPath = targetFile.absolutePath)
      } else {
        PlatformBinaryFileWriteResult.Failure("Cannot write target file")
      }
    }

private fun syncNoMediaFlag(rootDirectory: File, allowMediaIndexing: Boolean) {
  if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
    error("Cannot create save directory")
  }
  val marker = File(rootDirectory, ".nomedia")
  if (allowMediaIndexing) {
    if (marker.exists() && !marker.delete()) error("Cannot delete .nomedia")
  } else if (!marker.exists()) {
    marker.writeText("")
  }
}
