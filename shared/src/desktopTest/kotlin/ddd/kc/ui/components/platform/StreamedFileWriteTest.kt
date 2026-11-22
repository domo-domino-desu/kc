package ddd.kc.ui.components.platform

import ddd.kc.data.remote.media.downloadMediaTo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class StreamedFileWriteTest {
  @Test
  fun networkDownloadWritesBoundedChunksToAnAtomicDestination() = runTest {
    val payload = ByteArray(1024 * 1024 + 17) { (it % 251).toByte() }
    val client = HttpClient(MockEngine { respond(ByteReadChannel(payload), HttpStatusCode.OK) })
    val directory = Files.createTempDirectory("kc-streamed-download").toFile()
    val target = directory.resolve("video.mp4")
    try {
      var largest = 0
      var chunks = 0
      val result =
          writeBinaryFile(
              PlatformBinaryFileWriteRequest(
                  destination = PlatformBinaryFileDestination.File(target.absolutePath),
                  fileName = target.name,
                  transfer = { write ->
                    client.downloadMediaTo("https://example.invalid/file") { buffer, count ->
                      largest = maxOf(largest, count)
                      chunks++
                      write(buffer, count)
                    }
                  },
              )
          )
      assertIs<PlatformBinaryFileWriteResult.Saved>(result)
      assertContentEquals(payload, target.readBytes())
      assertTrue(largest <= 64 * 1024)
      assertTrue(chunks > 1)
      assertEquals(listOf(target.name), directory.listFiles()!!.map { it.name })
    } finally {
      client.close()
      directory.deleteRecursively()
    }
  }

  @Test
  fun failedOrCancelledTransferKeepsExistingFileAndRemovesPartialOutput() = runTest {
    val directory = Files.createTempDirectory("kc-streamed-rollback").toFile()
    val target = directory.resolve("archive.zip")
    try {
      target.writeText("existing complete file")
      val failed =
          writeBinaryFile(
              PlatformBinaryFileWriteRequest(
                  destination = PlatformBinaryFileDestination.File(target.absolutePath),
                  fileName = target.name,
                  transfer = { write ->
                    write(byteArrayOf(1, 2, 3), 3)
                    throw IOException("connection closed")
                  },
              )
          )
      assertIs<PlatformBinaryFileWriteResult.Failure>(failed)
      assertEquals("existing complete file", target.readText())
      assertFailsWith<CancellationException> {
        writeBinaryFile(
            PlatformBinaryFileWriteRequest(
                destination = PlatformBinaryFileDestination.File(target.absolutePath),
                fileName = target.name,
                transfer = { write ->
                  write(byteArrayOf(1), 1)
                  throw CancellationException("cancel")
                },
            )
        )
      }
      assertEquals("existing complete file", target.readText())
      assertEquals(listOf(target.name), directory.listFiles()!!.map { it.name })
    } finally {
      directory.deleteRecursively()
    }
  }
}
