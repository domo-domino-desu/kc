package ddd.kc.ui.components.platform

import androidx.compose.runtime.Composable

sealed interface PlatformBinaryFileDestination {
  data class Directory(
      val path: String,
      val relativeDirectories: List<String> = emptyList(),
  ) : PlatformBinaryFileDestination

  data class File(val path: String) : PlatformBinaryFileDestination
}

data class PlatformBinaryFileWriteRequest(
    val destination: PlatformBinaryFileDestination,
    val fileName: String,
    val bytes: ByteArray,
    val mimeType: String = "application/octet-stream",
)

sealed interface PlatformBinaryFileWriteResult {
  data class Saved(val savedPath: String) : PlatformBinaryFileWriteResult

  data class Failure(val message: String) : PlatformBinaryFileWriteResult
}

@Composable
expect fun rememberPlatformBinaryFileWriter():
    suspend (PlatformBinaryFileWriteRequest) -> PlatformBinaryFileWriteResult
