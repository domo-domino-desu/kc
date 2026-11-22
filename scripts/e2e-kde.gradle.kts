import groovy.json.JsonSlurper
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import org.gradle.api.tasks.JavaExec

// Window placement only. Test actions run in the actual Tao ComposeScene, not through KWin.
val useE2eDesktop =
    providers
        .gradleProperty("e2e.kdeDesktop")
        .map(String::toBoolean)
        .getOrElse(System.getenv("XDG_CURRENT_DESKTOP").orEmpty().contains("KDE", true))
val e2eRuleId = "codex-online-e2e-${rootProject.projectDir.name.lowercase()}"

fun desktopCommand(vararg args: String): String {
  val process = ProcessBuilder(*args).redirectError(ProcessBuilder.Redirect.INHERIT).start()
  val output = process.inputStream.bufferedReader().readText().trim()
  check(process.waitFor() == 0) { "E2E desktop setup failed: ${args.first()}" }
  return output
}

val configureE2eDesktop =
    tasks.register("configureE2eDesktop") {
      group = "verification"
      description = "Place only this app's E2E window on the configured KDE virtual desktop"
      onlyIf { useE2eDesktop }
      doLast {
        val response =
            desktopCommand(
                "busctl",
                "--user",
                "--json=short",
                "get-property",
                "org.kde.KWin",
                "/VirtualDesktopManager",
                "org.kde.KWin.VirtualDesktopManager",
                "desktops",
            )
        val desktops = (JsonSlurper().parseText(response) as Map<*, *>)["data"] as List<*>
        val ids = desktops.map { (it as List<*>)[1].toString() }
        val requested = providers.gradleProperty("e2e.desktopId").orNull
        check(ids.size > 1 || requested != null) {
          "Prepare a second KDE virtual desktop, or use -Pe2e.kdeDesktop=false"
        }
        val target = requested ?: ids.last()
        check(target in ids) { "Requested E2E desktop does not exist" }
        val title =
            tasks
                .named<JavaExec>("onlineE2e")
                .get()
                .systemProperties
                .getValue("e2e.windowTitle")
                .toString()
        val config =
            file(System.getenv("XDG_CONFIG_HOME") ?: "${System.getProperty("user.home")}/.config")
        config.mkdirs()
        FileChannel.open(
                config.resolve("codex-online-e2e.lock").toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            .use { channel ->
              channel.lock().use {
                val existing =
                    desktopCommand(
                            "kreadconfig6",
                            "--file",
                            "kwinrulesrc",
                            "--group",
                            "General",
                            "--key",
                            "rules",
                            "--default",
                            "",
                        )
                        .split(',')
                        .filter { it.isNotBlank() && it != "codex-online-e2e" }
                val rules = (existing + e2eRuleId).distinct()
                val entries =
                    mapOf(
                        "Description" to "$title (online E2E)",
                        "title" to title,
                        "titlematch" to "1",
                        "wmclassmatch" to "0",
                        "desktops" to target,
                        "desktopsrule" to "2",
                        "fsplevel" to "4",
                        "fsplevelrule" to "2",
                        // Keep activation blocked until the Kotlin runner needs human CF/login
                        // input.
                        "acceptfocus" to "false",
                        "acceptfocusrule" to "2",
                        "maximizevert" to "false",
                        "maximizevertrule" to "2",
                        "maximizehoriz" to "false",
                        "maximizehorizrule" to "2",
                        "size" to "1280,900",
                        "sizerule" to "2",
                    )
                entries.forEach { (key, value) ->
                  desktopCommand(
                      "kwriteconfig6",
                      "--file",
                      "kwinrulesrc",
                      "--group",
                      e2eRuleId,
                      "--key",
                      key,
                      value,
                  )
                }
                desktopCommand(
                    "kwriteconfig6",
                    "--file",
                    "kwinrulesrc",
                    "--group",
                    "General",
                    "--key",
                    "rules",
                    rules.joinToString(","),
                )
                desktopCommand(
                    "kwriteconfig6",
                    "--file",
                    "kwinrulesrc",
                    "--group",
                    "General",
                    "--key",
                    "count",
                    rules.size.toString(),
                )
                desktopCommand("qdbus", "org.kde.KWin", "/KWin", "org.kde.KWin.reconfigure")
              }
            }
        logger.lifecycle(
            "E2E window pinned to KDE desktop $target; activation blocked until human verification"
        )
      }
    }

val restoreE2eFocus =
    tasks.register("restoreE2eFocus") {
      onlyIf { useE2eDesktop }
      doLast {
        desktopCommand(
            "kwriteconfig6",
            "--file",
            "kwinrulesrc",
            "--group",
            e2eRuleId,
            "--key",
            "acceptfocusrule",
            "0",
        )
        desktopCommand("qdbus", "org.kde.KWin", "/KWin", "org.kde.KWin.reconfigure")
      }
    }

tasks.named<JavaExec>("onlineE2e") {
  dependsOn(configureE2eDesktop)
  finalizedBy(restoreE2eFocus)
  if (useE2eDesktop) systemProperty("e2e.kdeRule", e2eRuleId)
}
