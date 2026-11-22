package ddd.kc.desktop.e2e

import androidx.compose.runtime.LaunchedEffect
import ddd.kc.desktop.runDesktopApplication
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Separate headful JVM entrypoint; standard unit tests never open windows or access the network.
 */
fun main() {
  var completed = false
  var passed = false
  runDesktopApplication { content, close ->
    content()
    val adapter = rememberTaoSceneAdapter()
    LaunchedEffect(adapter) {
      // Finalization must stay on Tao's dispatcher too: returning to an occluded scene's
      // effect dispatcher would wait for a compositor frame that might never arrive.
      withContext(Dispatchers.Main) {
        val ui = TaoUiDriver(adapter)
        val suite = TaoOnlineSuite(ui)
        try {
          passed = suite.run(onlineScenarios(ui))
          completed = true
        } catch (error: CancellationException) {
          throw error
        } finally {
          adapter.dispose()
          close(if (completed && passed) 0 else 1)
        }
      }
    }
  }
  check(completed && passed) { "Online Tao E2E did not pass; see the sanitized report" }
}

internal class TaoOnlineSuite(private val ui: TaoUiDriver) {
  private data class Result(
      val name: String,
      val passed: Boolean,
      val seconds: Double,
      val step: String,
  )

  suspend fun run(cases: LinkedHashMap<String, suspend () -> Unit>): Boolean {
    val selected = System.getProperty("e2e.case").orEmpty()
    check(selected.isBlank() || selected in cases) { "Unknown E2E case" }
    val results = mutableListOf<Result>()
    try {
      for ((name, execute) in cases) {
        if (selected.isNotBlank() && selected != name) continue
        println("E2E RUN $name")
        val start = System.nanoTime()
        try {
          ui.root()
          execute()
          results += Result(name, true, (System.nanoTime() - start) / 1e9, "complete")
          println("E2E PASS $name")
        } catch (error: CancellationException) {
          throw error
        } catch (error: Throwable) {
          // A throwable can contain private URLs/account names. Reports only contain fixed
          // case/step IDs.
          results += Result(name, false, (System.nanoTime() - start) / 1e9, ui.step)
          val source = error.stackTrace.firstOrNull { it.className.contains(".desktop.e2e.") }
          println(
              "E2E FAIL $name at ${ui.step} (${error.javaClass.simpleName}; ${source?.fileName}:${source?.lineNumber})"
          )
          try {
            ui.root()
          } catch (_: Throwable) {
            return false
          }
        }
      }
      return results.isNotEmpty() && results.all { it.passed }
    } finally {
      val directory = File(System.getProperty("e2e.reportDir"))
      directory.mkdirs()
      val full = selected.isBlank() && results.size == cases.size && results.all { it.passed }
      val entries =
          results.joinToString(",\n") {
            "    {\"case\":\"${it.name}\",\"passed\":${it.passed},\"seconds\":${it.seconds},\"step\":\"${it.step}\"}"
          }
      File(directory, "report.json")
          .writeText(
              """
        {"backend":"Tao","driver":"ComposeScene","network":"live","fullCoveragePassed":$full,"humanWaits":${ui.humanWaits},"results":[
        $entries
        ]}
        """
                  .trimIndent() + "\n"
          )
    }
  }
}

/**
 * Initial map must not activate another workspace; afterwards users can focus CF/login normally.
 */
internal suspend fun restoreManualFocus() {
  val rule = System.getProperty("e2e.kdeRule") ?: return
  kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    for (command in
        listOf(
            listOf(
                "kwriteconfig6",
                "--file",
                "kwinrulesrc",
                "--group",
                rule,
                "--key",
                "acceptfocusrule",
                "0",
            ),
            listOf("qdbus", "org.kde.KWin", "/KWin", "org.kde.KWin.reconfigure"),
        )) {
      val process =
          ProcessBuilder(command)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start()
      check(process.waitFor() == 0) { "Could not restore manual focus for E2E verification" }
    }
  }
}
