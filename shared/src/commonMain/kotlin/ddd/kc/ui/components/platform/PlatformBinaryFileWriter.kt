package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable

sealed interface PlatformBinaryFileDestination {
  data class Directory(
      val path: String,
      val relativeDirectories: List<String> = emptyList(),
      val allowMediaIndexing: Boolean = true,
  ) : PlatformBinaryFileDestination

  data class File(val path: String) : PlatformBinaryFileDestination
}

data class PlatformBinaryFileWriteRequest(
    val destination: PlatformBinaryFileDestination,
    val fileName: String,
    val bytes: ByteArray = byteArrayOf(),
    val mimeType: String = "application/octet-stream",
    val transfer: (suspend (write: (ByteArray, Int) -> Unit) -> Unit)? = null,
)

suspend fun PlatformBinaryFileWriteRequest.writeContent(write: (ByteArray, Int) -> Unit) {
  val stream = transfer
  if (stream != null) stream(write) else write(bytes, bytes.size)
}

sealed interface PlatformBinaryFileWriteResult {
  data class Saved(val savedPath: String) : PlatformBinaryFileWriteResult

  data class Failure(val message: String) : PlatformBinaryFileWriteResult
}

@Composable
expect fun rememberPlatformBinaryFileWriter():
    suspend (PlatformBinaryFileWriteRequest) -> PlatformBinaryFileWriteResult
