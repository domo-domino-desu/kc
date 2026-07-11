package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.text.Charsets.UTF_8
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
                run {
                  if (!hasSafeRelativePath(destination.relativeDirectories, request.fileName)) {
                    return@withContext PlatformTextFileWriteResult.Failure("Unsafe relative path")
                  }
                  val root = File(destination.path).canonicalFile
                  val target =
                      destination.relativeDirectories
                          .fold(root) { current, segment -> File(current, segment) }
                          .resolve(request.fileName)
                          .canonicalFile
                  if (!target.toPath().startsWith(root.toPath())) {
                    return@withContext PlatformTextFileWriteResult.Failure("Unsafe relative path")
                  }
                  target
                }
            is PlatformTextFileDestination.File -> File(destination.path)
          }

      val parent = targetFile.parentFile
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        return@withContext PlatformTextFileWriteResult.Failure("Cannot create parent directory")
      }

      val writeSuccess = runCatching { atomicWrite(targetFile, request.content) }.isSuccess
      if (writeSuccess) {
        PlatformTextFileWriteResult.Saved(savedPath = targetFile.absolutePath)
      } else {
        PlatformTextFileWriteResult.Failure("Cannot write target file")
      }
    }

private fun atomicWrite(target: File, content: String) {
  val parent = requireNotNull(target.parentFile)
  val temporary = Files.createTempFile(parent.toPath(), ".kc-", ".tmp")
  try {
    Files.write(temporary, content.toByteArray(UTF_8))
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
