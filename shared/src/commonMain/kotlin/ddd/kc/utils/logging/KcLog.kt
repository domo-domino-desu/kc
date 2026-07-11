package ddd.kc.utils.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlin.time.Clock

object KcLog {
  private val stateLock = Any()
  private var initialized: Boolean = false
  private var minSeveritySnapshot: Severity = Severity.Info
  private val runtimeLogBuffer = RuntimeLogBuffer()
  private val runtimeLogWriter =
      object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
          val safeMessage = redactLogText(message)
          runtimeLogBuffer.append(
              buildString {
                append("[")
                append(currentLogTimestamp())
                append("] [")
                append(severity.name)
                append("] [")
                append(tag.ifBlank { "-" })
                append("] ")
                append(safeMessage)
                if (throwable != null) {
                  appendLine()
                  append("  -> ")
                  append(throwable::class.simpleName ?: "Throwable")
                }
              }
          )
        }
      }

  fun init(minSeverity: Severity) {
    synchronized(stateLock) {
      minSeveritySnapshot = minSeverity
      Logger.setLogWriters(RedactingLogWriter(platformLogWriter()), runtimeLogWriter)
      Logger.setMinSeverity(minSeverity)
      initialized = true
    }
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
    val (severity, wasInitialized) = synchronized(stateLock) { minSeveritySnapshot to initialized }
    return buildString {
      appendLine("kc log export")
      appendLine("version=$appVersionName")
      appendLine("exported_at=$exportedAt")
      appendLine("min_severity=${severity.name}")
      appendLine("initialized=$wasInitialized")
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

private class RedactingLogWriter(private val delegate: LogWriter) : LogWriter() {
  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    val exceptionType = throwable?.let { " [${it::class.simpleName ?: "Throwable"}]" }.orEmpty()
    delegate.log(severity, redactLogText(message) + exceptionType, tag, null)
  }
}

internal fun redactLogText(value: String): String =
    value
        .replace(BEARER_SECRET, "$1<redacted>")
        .replace(COOKIE_SECRET, "$1<redacted>")
        .replace(QUERY_SECRET, "$1<redacted>")
        .replace(URL_QUERY, "$1?<redacted>")

private val BEARER_SECRET = Regex("(?i)(bearer\\s+)[^\\s,;]+")
private val COOKIE_SECRET = Regex("(?i)((?:session|api[_-]?key|token)\\s*[=:]\\s*)[^\\s,;]+")
private val QUERY_SECRET = Regex("(?i)((?:q|query|api[_-]?key|token|session)=)[^&\\s]+")
private val URL_QUERY = Regex("([a-zA-Z][a-zA-Z0-9+.-]*://[^?\\s]+)\\?[^\\s]+")

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
