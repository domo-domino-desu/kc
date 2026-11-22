package ddd.kc.ui.components.platform

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
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

private suspend fun writeBinaryFileIntoDirectory(
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
  if (!hasSafeRelativePath(destination.relativeDirectories, request.fileName)) {
    return PlatformBinaryFileWriteResult.Failure("Unsafe relative path")
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

  if (request.transfer != null) {
    val suffix = Random.nextLong().toString().replace('-', '0')
    val temporary =
        targetDirectory.createFile(request.mimeType, "kc-$suffix.part")
            ?: return PlatformBinaryFileWriteResult.Failure("Cannot create temporary file")
    var committed = false
    var backup: DocumentFile? = null
    try {
      val result = writeBinaryFileToDocumentUri(request, temporary.uri.toString(), context)
      if (result is PlatformBinaryFileWriteResult.Failure) return result
      val existing = targetDirectory.findFile(request.fileName)
      if (existing != null) {
        if (!existing.renameTo("kc-$suffix.backup"))
            return PlatformBinaryFileWriteResult.Failure("Cannot replace target file")
        backup = existing
      }
      if (!temporary.renameTo(request.fileName))
          return PlatformBinaryFileWriteResult.Failure("Cannot finalize target file")
      committed = true
      backup?.delete()
      return PlatformBinaryFileWriteResult.Saved(temporary.uri.toString())
    } finally {
      if (!committed) {
        temporary.delete()
        backup?.renameTo(request.fileName)
      }
    }
  }

  val document =
      targetDirectory.findFile(request.fileName)
          ?: targetDirectory.createFile(request.mimeType, request.fileName)
  if (document == null) {
    return PlatformBinaryFileWriteResult.Failure("Cannot create target file")
  }
  return writeBinaryFileToDocumentUri(request, document.uri.toString(), context)
}

private suspend fun writeBinaryFileToDocumentUri(
    request: PlatformBinaryFileWriteRequest,
    uriPath: String,
    context: Context,
): PlatformBinaryFileWriteResult {
  val targetUri = runCatching { Uri.parse(uriPath) }.getOrNull()
  if (targetUri == null || targetUri.scheme?.lowercase() != "content") {
    return PlatformBinaryFileWriteResult.Failure("Invalid target file")
  }

  val writeSuccess =
      try {
        context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
          request.writeContent { bytes, count -> output.write(bytes, 0, count) }
        } ?: error("Cannot open output stream")
        true
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        false
      }
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
