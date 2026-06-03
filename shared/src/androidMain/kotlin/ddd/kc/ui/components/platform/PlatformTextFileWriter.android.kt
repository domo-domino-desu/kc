package ddd.kc.ui.components.platform

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformTextFileWriter():
    suspend (PlatformTextFileWriteRequest) -> PlatformTextFileWriteResult {
  val context = LocalContext.current
  return remember(context) { { request -> writeTextFile(request, context) } }
}

private suspend fun writeTextFile(
    request: PlatformTextFileWriteRequest,
    context: Context,
): PlatformTextFileWriteResult =
    withContext(Dispatchers.IO) {
      when (val destination = request.destination) {
        is PlatformTextFileDestination.Directory ->
            writeTextFileIntoDirectory(request, destination, context)

        is PlatformTextFileDestination.File ->
            writeTextFileToDocumentUri(request, destination.path, context)
      }
    }

private fun writeTextFileIntoDirectory(
    request: PlatformTextFileWriteRequest,
    destination: PlatformTextFileDestination.Directory,
    context: Context,
): PlatformTextFileWriteResult {
  val treeUri = runCatching { Uri.parse(destination.path) }.getOrNull()
  if (treeUri == null || treeUri.scheme?.lowercase() != "content") {
    return PlatformTextFileWriteResult.Failure("Invalid directory path")
  }
  val rootDirectory = DocumentFile.fromTreeUri(context, treeUri)
  if (rootDirectory == null || !rootDirectory.isDirectory) {
    return PlatformTextFileWriteResult.Failure("Cannot access directory")
  }
  val targetDirectory =
      destination.relativeDirectories.fold(rootDirectory) { current, segment ->
        current.findFile(segment)?.takeIf { file -> file.isDirectory }
            ?: current.createDirectory(segment)
            ?: return PlatformTextFileWriteResult.Failure("Cannot create parent directory")
      }

  targetDirectory.findFile(request.fileName)?.delete()
  val document = targetDirectory.createFile("text/plain", request.fileName)
  if (document == null) {
    return PlatformTextFileWriteResult.Failure("Cannot create target file")
  }
  return writeTextFileToDocumentUri(request, document.uri.toString(), context)
}

private fun writeTextFileToDocumentUri(
    request: PlatformTextFileWriteRequest,
    uriPath: String,
    context: Context,
): PlatformTextFileWriteResult {
  val targetUri = runCatching { Uri.parse(uriPath) }.getOrNull()
  if (targetUri == null || targetUri.scheme?.lowercase() != "content") {
    return PlatformTextFileWriteResult.Failure("Invalid target file")
  }

  val writeSuccess =
      runCatching {
            context.contentResolver.openOutputStream(targetUri, "w")?.bufferedWriter()?.use {
              it.write(request.content)
            } ?: error("Cannot open output stream")
          }
          .isSuccess
  return if (writeSuccess) {
    PlatformTextFileWriteResult.Saved(savedPath = uriPath)
  } else {
    PlatformTextFileWriteResult.Failure("Cannot write target file")
  }
}
