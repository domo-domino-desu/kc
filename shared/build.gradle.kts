import groovy.json.JsonSlurper

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.aboutLibraries)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.symbolCraft)
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val generatedAboutMetadataDir =
    layout.buildDirectory.dir("generated/aboutMetadata/commonMain/kotlin")
val generatedSymbolCraftDir = layout.buildDirectory.dir("generated/symbolcraft/commonMain/kotlin")
val generateAboutMetadata =
    tasks.register("generateAboutMetadata") {
      val licenseFile = rootProject.file("LICENSE")
      inputs.property("appVersionName", appVersionName)
      inputs.file(licenseFile)
      outputs.dir(generatedAboutMetadataDir)

      doLast {
        val outputFile =
            generatedAboutMetadataDir.get().file("ddd/kc/generated/AboutMetadata.kt").asFile
        val licenseLiteral = buildString {
          append("\"\"\"")
          append(licenseFile.readText().replace("\"\"\"", "\"\"\\\"").replace("$", "\${'$'}"))
          append("\"\"\".trimIndent()")
        }
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package ddd.kc.generated

            internal object AboutMetadata {
              const val versionName: String = "$appVersionName"

              val licenseText: String = $licenseLiteral
            }
            """
                .trimIndent()
        )
      }
    }

kotlin {
  val androidCompileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").map(String::toInt).get()
  val androidMinSdk = providers.gradleProperty("ANDROID_MIN_SDK").map(String::toInt).get()
  android {
    namespace = "ddd.kc.shared"
    compileSdk = androidCompileSdk
    minSdk = androidMinSdk
    androidResources { enable = true }
  }

  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      kotlin.srcDir(generatedAboutMetadataDir)
      kotlin.srcDir(generatedSymbolCraftDir)
      dependencies {
        implementation(libs.aboutlibraries.compose.m3)
        implementation(libs.aboutlibraries.core)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.ktor3)
        implementation(libs.composemediaplayer)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.runtime)
        implementation(libs.compose.ui)
        implementation(libs.compose.ui.backhandler)
        implementation(libs.datastore.preferences.core)
        implementation(libs.htmlconverter.compose)
        implementation(libs.kermit)
        implementation(libs.koin.compose)
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ksafe)
        implementation(libs.ksoup)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.content.negotiation)
        implementation(libs.ktor.serialization.json)
        implementation(libs.materialyou)
        implementation(libs.room.runtime)
        implementation(libs.voyager.core)
        implementation(libs.voyager.koin)
        implementation(libs.voyager.navigator)
        implementation(libs.voyager.screenmodel)
        implementation(libs.voyager.tab.navigator)
        implementation(libs.zoomimage.compose.coil3)
      }
    }

    val desktopMain by getting {
      dependencies {
        implementation(libs.ktor.client.okhttp)
        implementation(libs.slf4j.simple)
        implementation(libs.materialyou)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.androidx.activity.compose)
        implementation(libs.coil.gif)
        implementation(libs.documentfile)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.materialyou)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.ktor.client.mock)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.kotlinx.coroutines.core)
      }
    }
  }
}

dependencies {
  add("kspCommonMainMetadata", libs.room.compiler)
  add("kspDesktop", libs.room.compiler)
  add("kspAndroid", libs.room.compiler)
}

room { schemaDirectory("$projectDir/schemas") }

aboutLibraries {
  export { outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json") }
}

val verifyAboutLibrariesMetadata =
    tasks.register("verifyAboutLibrariesMetadata") {
      val metadata = file("src/commonMain/composeResources/files/aboutlibraries.json")
      dependsOn("exportLibraryDefinitions")
      inputs.file(metadata)
      doLast {
        val root =
            JsonSlurper().parse(metadata) as? Map<*, *>
                ?: error("aboutlibraries.json must contain a JSON object")
        val libraries = root["libraries"] as? Collection<*>
        check(!libraries.isNullOrEmpty()) { "aboutlibraries.json must contain libraries" }
      }
    }

tasks
    .matching { it.name == "copyNonXmlValueResourcesForCommonMain" }
    .configureEach { mustRunAfter("exportLibraryDefinitions") }

tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyAboutLibrariesMetadata) }

symbolCraft {
  packageName.set("ddd.kc.generated.symbols")
  outputDirectory.set(generatedSymbolCraftDir.get().asFile.absolutePath)
  generatePreview.set(false)
  cacheEnabled.set(true)

  naming { pascalCase() }

  materialSymbols(
      "explore",
      "favorite",
      "hd",
      "menu",
      "search",
      "tag",
      "person",
      "image",
      "comment",
  ) {
    style(
        weight = 400,
        variant = io.github.kingsword09.symbolcraft.model.SymbolVariant.OUTLINED,
        fill = io.github.kingsword09.symbolcraft.model.SymbolFill.FILLED,
    )
  }

  materialSymbols(
      "arrow_back",
      "arrow_upward",
      "attribution",
      "attach_file",
      "calendar_add_on",
      "code",
      "close",
      "comment",
      "date_range",
      "download",
      "expand_more",
      "explore",
      "favorite",
      "filter_alt",
      "forward_10",
      "forward_30",
      "fullscreen",
      "fullscreen_exit",
      "hd",
      "home",
      "image",
      "keyboard_arrow_left",
      "keyboard_arrow_right",
      "link",
      "lock_open",
      "logout",
      "menu",
      "more_horiz",
      "open_in_new",
      "output_circle",
      "person",
      "pause",
      "play_arrow",
      "refresh",
      "replay_10",
      "replay_30",
      "receipt_long",
      "search",
      "settings",
      "share",
      "sort",
      "tag",
      "translate",
      "vertical_align_top",
      "comment",
  ) {
    style(
        weight = 400,
        variant = io.github.kingsword09.symbolcraft.model.SymbolVariant.OUTLINED,
        fill = io.github.kingsword09.symbolcraft.model.SymbolFill.UNFILLED,
    )
  }

  externalIcons("discord", libraryName = "fontawesomebrands") {
    urlTemplate = "https://api.iconify.design/fa7-brands:{name}.svg"
  }

  externalIcons("github", libraryName = "fontawesomebrands") {
    urlTemplate = "https://api.iconify.design/fa7-brands:{name}.svg"
  }

  externalIcons("patreon", libraryName = "mdi") {
    urlTemplate = "https://api.iconify.design/mdi:{name}.svg"
  }

  externalIcons(
      "afdian",
      "boosty",
      "gumroad",
      "onlyfans",
      "pixiv",
      libraryName = "simpleicons",
  ) {
    urlTemplate = "https://api.iconify.design/simple-icons:{name}.svg"
  }

  externalIcons("fansly", libraryName = "arcticons") {
    urlTemplate = "https://api.iconify.design/arcticons:{name}.svg"
  }

  externalIcons("section", libraryName = "lucide") {
    urlTemplate = "https://api.iconify.design/lucide:{name}.svg"
  }

  localIcons(libraryName = "service") { directory = "../assets" }
}

val symbolCraftConsumerTasks =
    setOf(
        "compileCommonMainKotlinMetadata",
        "kspCommonMainKotlinMetadata",
        "compileAndroidMain",
        "kspAndroidMain",
        "compileKotlinDesktop",
        "kspKotlinDesktop",
    )

tasks.configureEach {
  if (name in symbolCraftConsumerTasks) {
    dependsOn("generateSymbolCraftIcons")
  }
  if (name in symbolCraftConsumerTasks || name == "compileCommonMainKotlinMetadata") {
    dependsOn(generateAboutMetadata)
  }
}
