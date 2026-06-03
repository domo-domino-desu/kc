package ddd.kc.ui.pages.about

import kc.shared.generated.resources.Res

private const val aboutLibrariesResourcePath = "files/aboutlibraries.json"
private const val emptyAboutLibrariesJson = """{"licenses":{},"libraries":[]}"""

internal suspend fun loadAboutLibrariesJson(): String {
  val composeResourceJson =
      runCatching { Res.readBytes(aboutLibrariesResourcePath).decodeToString() }.getOrNull()
  if (!composeResourceJson.isNullOrBlank()) return composeResourceJson

  return loadPlatformAboutLibrariesJsonOrNull()?.takeIf { it.isNotBlank() }
      ?: emptyAboutLibrariesJson
}

internal expect suspend fun loadPlatformAboutLibrariesJsonOrNull(): String?
