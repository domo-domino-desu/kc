import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val desktopPackageVersion =
    Regex("""^(\d+\.\d+\.\d+)(?:[-+].*)?$""").matchEntire(appVersionName)?.groupValues?.get(1)
        ?: error("APP_VERSION_NAME must be SemVer (e.g. 1.2.3 or 1.2.3-alpha1)")

kotlin {
  jvm("desktop")

  sourceSets {
    val desktopMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.material3)
        implementation(libs.coil.compose)
        implementation(libs.datastore.preferences.core)
        implementation(libs.ksafe)
        implementation(project(":shared"))
        implementation(libs.koin.core)
        implementation(libs.kermit)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
        implementation(libs.slf4j.simple)
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "ddd.kc.desktop.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Deb, TargetFormat.Msi)
      packageName = "kc"
      packageVersion = desktopPackageVersion
    }
  }
}
