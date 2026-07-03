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
actual fun rememberPlatformBinaryFileWriter():
    suspend (PlatformBinaryFileWriteRequest) -> PlatformBinaryFileWriteResult {
  val context = LocalContext.current
  return remember(context) { { request -> writeBinaryFile(request, context) } }
}

private suspend fun writeBinaryFile(
    request: PlatformBinaryFileWriteRequest,
    context: Context,
): PlatformBinaryFileWriteResult =
    withContext(Dispatchers.IO) {
      when (val destination = request.destination) {
        is PlatformBinaryFileDestination.Directory ->
            writeBinaryFileIntoDirectory(request, destination, context)

        is PlatformBinaryFileDestination.File ->
            writeBinaryFileToDocumentUri(request, destination.path, context)
      }
    }

private fun writeBinaryFileIntoDirectory(
    request: PlatformBinaryFileWriteRequest,
    destination: PlatformBinaryFileDestination.Directory,
    context: Context,
): PlatformBinaryFileWriteResult {
  val treeUri = runCatching { Uri.parse(destination.path) }.getOrNull()
  if (treeUri == null || treeUri.scheme?.lowercase() != "content") {
    return PlatformBinaryFileWriteResult.Failure("Invalid directory path")
  }
  val rootDirectory = DocumentFile.fromTreeUri(context, treeUri)
  if (rootDirectory == null || !rootDirectory.isDirectory) {
    return PlatformBinaryFileWriteResult.Failure("Cannot access directory")
  }
  runCatching { syncNoMediaFlag(rootDirectory, destination.allowMediaIndexing) }
      .onFailure {
        return PlatformBinaryFileWriteResult.Failure(it.message ?: "Cannot sync .nomedia")
      }
  val targetDirectory =
      destination.relativeDirectories.fold(rootDirectory) { current, segment ->
        current.findFile(segment)?.takeIf { file -> file.isDirectory }
            ?: current.createDirectory(segment)
            ?: return PlatformBinaryFileWriteResult.Failure("Cannot create parent directory")
      }

  targetDirectory.findFile(request.fileName)?.delete()
  val document = targetDirectory.createFile(request.mimeType, request.fileName)
  if (document == null) {
    return PlatformBinaryFileWriteResult.Failure("Cannot create target file")
  }
  return writeBinaryFileToDocumentUri(request, document.uri.toString(), context)
}

private fun writeBinaryFileToDocumentUri(
    request: PlatformBinaryFileWriteRequest,
    uriPath: String,
    context: Context,
): PlatformBinaryFileWriteResult {
  val targetUri = runCatching { Uri.parse(uriPath) }.getOrNull()
  if (targetUri == null || targetUri.scheme?.lowercase() != "content") {
    return PlatformBinaryFileWriteResult.Failure("Invalid target file")
  }

  val writeSuccess =
      runCatching {
            context.contentResolver.openOutputStream(targetUri, "w")?.use {
              it.write(request.bytes)
            } ?: error("Cannot open output stream")
          }
          .isSuccess
  return if (writeSuccess) {
    PlatformBinaryFileWriteResult.Saved(savedPath = uriPath)
  } else {
    PlatformBinaryFileWriteResult.Failure("Cannot write target file")
  }
}

private fun syncNoMediaFlag(rootDirectory: DocumentFile, allowMediaIndexing: Boolean) {
  val marker = rootDirectory.findFile(".nomedia")
  if (allowMediaIndexing) {
    if (marker != null && !marker.delete()) error("Cannot delete .nomedia")
  } else if (marker == null) {
    rootDirectory.createFile("application/octet-stream", ".nomedia")
        ?: error("Cannot create .nomedia")
  }
}
