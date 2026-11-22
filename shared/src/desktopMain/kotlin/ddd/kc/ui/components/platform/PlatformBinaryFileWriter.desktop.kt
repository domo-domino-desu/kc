package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformBinaryFileWriter():
    suspend (PlatformBinaryFileWriteRequest) -> PlatformBinaryFileWriteResult {
  return remember { { request -> writeBinaryFile(request) } }
}

internal suspend fun writeBinaryFile(
    request: PlatformBinaryFileWriteRequest
): PlatformBinaryFileWriteResult =
    withContext(Dispatchers.IO) {
      val targetFile =
          when (val destination = request.destination) {
            is PlatformBinaryFileDestination.Directory -> {
              val rootDirectory = File(destination.path)
              if (!hasSafeRelativePath(destination.relativeDirectories, request.fileName)) {
                return@withContext PlatformBinaryFileWriteResult.Failure("Unsafe relative path")
              }
              runCatching { syncNoMediaFlag(rootDirectory, destination.allowMediaIndexing) }
                  .onFailure {
                    return@withContext PlatformBinaryFileWriteResult.Failure(
                        it.message ?: "Cannot sync .nomedia"
                    )
                  }
              val target =
                  destination.relativeDirectories
                      .fold(rootDirectory) { current, segment -> File(current, segment) }
                      .resolve(request.fileName)
              val canonicalRoot = rootDirectory.canonicalFile
              val canonicalTarget = target.canonicalFile
              if (!canonicalTarget.toPath().startsWith(canonicalRoot.toPath())) {
                return@withContext PlatformBinaryFileWriteResult.Failure("Unsafe relative path")
              }
              canonicalTarget
            }
            is PlatformBinaryFileDestination.File -> File(destination.path)
          }

      val parent = targetFile.parentFile
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        return@withContext PlatformBinaryFileWriteResult.Failure("Cannot create parent directory")
      }

      val writeSuccess =
          try {
            atomicWrite(targetFile, request)
            true
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            false
          }
      if (writeSuccess) {
        PlatformBinaryFileWriteResult.Saved(savedPath = targetFile.absolutePath)
      } else {
        PlatformBinaryFileWriteResult.Failure("Cannot write target file")
      }
    }

private suspend fun atomicWrite(target: File, request: PlatformBinaryFileWriteRequest) {
  val parent = requireNotNull(target.parentFile)
  val temporary = Files.createTempFile(parent.toPath(), ".kc-", ".tmp")
  try {
    Files.newOutputStream(temporary).use { output ->
      request.writeContent { bytes, count -> output.write(bytes, 0, count) }
    }
    try {
      Files.move(
          temporary,
          target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  } finally {
    Files.deleteIfExists(temporary)
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
