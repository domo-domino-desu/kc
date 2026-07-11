package ddd.kc.ui.pages.about

import kc.shared.generated.resources.Res

private const val aboutLibrariesResourcePath = "files/aboutlibraries.json"

internal suspend fun loadAboutLibrariesJson(): String =
    Res.readBytes(aboutLibrariesResourcePath).decodeToString().also {
      require(it.isNotBlank()) { "aboutlibraries.json is empty" }
    }
