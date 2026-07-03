package ddd.kc.utils.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlin.time.Clock

object KcLog {
  private var initialized: Boolean = false
  private var minSeveritySnapshot: Severity = Severity.Info
  private val runtimeLogBuffer = RuntimeLogBuffer()
  private val runtimeLogWriter =
      object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
          runtimeLogBuffer.append(
              buildString {
                append("[")
                append(currentLogTimestamp())
                append("] [")
                append(severity.name)
                append("] [")
                append(tag.ifBlank { "-" })
                append("] ")
                append(message)
                if (throwable != null) {
                  appendLine()
                  append("  -> ")
                  append(throwable.toString())
                }
              }
          )
        }
      }

  fun init(minSeverity: Severity) {
    minSeveritySnapshot = minSeverity
    Logger.setLogWriters(platformLogWriter(), runtimeLogWriter)
    Logger.setMinSeverity(minSeverity)
    initialized = true
    Logger.withTag("KcLog").i { "Initialized logging -> minSeverity=${minSeverity.name}" }
  }

  fun withTag(tag: String): Logger = Logger.withTag(tag)

  fun parseDesktopSeverity(raw: String?): Severity =
      when (raw?.trim()?.lowercase()) {
        "verbose",
        "trace",
        "v" -> Severity.Verbose
        "debug",
        "d" -> Severity.Debug
        "info",
        "i",
        null,
        "" -> Severity.Info
        "warn",
        "warning",
        "w" -> Severity.Warn
        "error",
        "e" -> Severity.Error
        "assert",
        "a" -> Severity.Assert
        else -> Severity.Info
      }

  fun exportRuntimeLogText(appVersionName: String): String {
    val exportedAt = currentLogTimestamp()
    val logs = runtimeLogBuffer.snapshot()
    return buildString {
      appendLine("kc log export")
      appendLine("version=$appVersionName")
      appendLine("exported_at=$exportedAt")
      appendLine("min_severity=${minSeveritySnapshot.name}")
      appendLine("initialized=$initialized")
      appendLine()
      if (logs.isBlank()) {
        appendLine("(no runtime logs captured)")
      } else {
        append(logs)
        if (!logs.endsWith('\n')) {
          appendLine()
        }
      }
    }
  }
}

private class RuntimeLogBuffer(
    private val maxEntries: Int = 500,
) {
  private val lock = Any()
  private val entries = ArrayDeque<String>()

  fun append(entry: String) {
    synchronized(lock) {
      if (entries.size >= maxEntries) {
        entries.removeFirst()
      }
      entries.addLast(entry)
    }
  }

  fun snapshot(): String =
      synchronized(lock) {
        if (entries.isEmpty()) {
          ""
        } else {
          entries.joinToString(separator = "\n")
        }
      }
}

private fun currentLogTimestamp(): String {
  return Clock.System.now().toString()
}
