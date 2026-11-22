import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.nucleus)
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val desktopPackageVersion =
    Regex("""^(\d+\.\d+\.\d+)(?:[-+].*)?$""").matchEntire(appVersionName)?.groupValues?.get(1)
        ?: error("APP_VERSION_NAME must be SemVer (e.g. 1.2.3 or 1.2.3-alpha1)")

kotlin {
  jvm("desktop")

  sourceSets {
    named("desktopTest") { dependencies { implementation(kotlin("test")) } }
    named("desktopMain") {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.material3)
        implementation(libs.coil.compose)
        implementation(libs.zoomimage.compose.coil3)
        implementation(libs.datastore.preferences.core)
        implementation(libs.ksafe)
        implementation(project(":shared"))
        implementation(libs.koin.core)
        implementation(libs.kermit)
        implementation(libs.nucleus.application)
        implementation(libs.nucleus.core.runtime)
        implementation(libs.nucleus.decorated.window.material3)
        implementation(libs.nucleus.decorated.window.tao)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
        implementation(libs.slf4j.simple)
      }
    }
  }
}

nucleus.application {
  from(kotlin.targets.getByName("desktop"))
  mainClass = "ddd.kc.desktop.MainKt"

  nativeDistributions {
    targetFormats(TargetFormat.Deb, TargetFormat.Msi)
    appName = "KC"
    packageName = "kc"
    packageVersion = desktopPackageVersion
  }
}

// A real Tao event loop must own the process/main thread. This does not use AWT ComposeTestRule.
val desktopE2eCompilation =
    (kotlin.targets.getByName("desktop") as KotlinJvmTarget).compilations.getByName("test")

tasks.withType<JavaExec>().configureEach {
  if (name in setOf("run", "onlineE2e") && System.getProperty("os.name").startsWith("Linux")) {
    // WebKitGTK's accelerated surface conflicts with this host's Tao EGL context.
    // Keep the real WebView and compositor; disable only WebKit's GPU surface path.
    environment(
        "WEBKIT_DISABLE_DMABUF_RENDERER",
        System.getenv("WEBKIT_DISABLE_DMABUF_RENDERER") ?: "1",
    )
    environment(
        "WEBKIT_DISABLE_COMPOSITING_MODE",
        System.getenv("WEBKIT_DISABLE_COMPOSITING_MODE") ?: "1",
    )
  }
}

tasks.register<JavaExec>("onlineE2e") {
  group = "verification"
  description = "Run Kotlin E2E in the real Tao desktop window against the live website"
  dependsOn(desktopE2eCompilation.compileTaskProvider)
  classpath(desktopE2eCompilation.output.allOutputs, desktopE2eCompilation.runtimeDependencyFiles)
  // A background test launch must not reuse the editor/terminal's activation intent.
  environment.remove("XDG_ACTIVATION_TOKEN")
  environment.remove("DESKTOP_STARTUP_ID")
  mainClass.set("ddd.kc.desktop.e2e.OnlineE2eMainKt")
  systemProperty(
      "e2e.reportDir",
      layout.buildDirectory.dir("reports/onlineE2e").get().asFile.absolutePath,
  )
  systemProperty("kc.log.level", "Error")
  systemProperty("e2e.windowTitle", "KC E2E")
  listOf(
          "case",
          "timeoutSeconds",
          "humanTimeoutSeconds",
          "query",
          "dmQuery",
          "postQuery",
          "communityCreatorName",
      )
      .forEach { key ->
        providers.gradleProperty("e2e.$key").orNull?.let { systemProperty("e2e.$key", it) }
      }
  if (!providers.gradleProperty("e2e.useLocalProfile").map(String::toBoolean).getOrElse(false)) {
    val profile = layout.buildDirectory.dir("onlineE2e/profile").get().asFile
    environment("KC_E2E_HOME", profile.absolutePath)
    doFirst { profile.mkdirs() }
  }
  if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true))
      jvmArgs("-XstartOnFirstThread")
  val report = layout.buildDirectory.file("reports/onlineE2e/report.json")
  doFirst { report.get().asFile.delete() }
  doLast {
    val file = report.get().asFile
    check(file.isFile) { "Tao exited without an E2E report; suite did not complete" }
    val result = file.readText()
    check(!result.contains("\"passed\":false") && result.contains("\"passed\":true")) {
      "Online Tao E2E failed; see ${file.absolutePath}"
    }
    if (providers.gradleProperty("e2e.case").orNull.isNullOrBlank()) {
      check(result.contains("\"fullCoveragePassed\":true")) {
        "Online E2E suite did not finish all cases"
      }
    }
  }
}

apply(from = rootProject.file("scripts/e2e-kde.gradle.kts"))
