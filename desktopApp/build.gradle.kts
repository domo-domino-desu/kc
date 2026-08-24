import dev.nucleusframework.desktop.application.dsl.TargetFormat

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
    named("desktopMain") {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.material3)
        implementation(libs.coil.compose)
        implementation(libs.datastore.preferences.core)
        implementation(libs.ksafe)
        implementation(project(":shared"))
        implementation(libs.koin.core)
        implementation(libs.kermit)
        implementation(libs.nucleus.application)
        implementation(libs.nucleus.core.runtime)
        implementation(libs.nucleus.decorated.window.tao)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
        implementation(libs.slf4j.simple)
      }
    }
  }
}

nucleus.application {
  mainClass = "ddd.kc.desktop.MainKt"

  nativeDistributions {
    targetFormats(TargetFormat.Deb, TargetFormat.Msi)
    appName = "KC"
    packageName = "kc"
    packageVersion = desktopPackageVersion
  }
}
