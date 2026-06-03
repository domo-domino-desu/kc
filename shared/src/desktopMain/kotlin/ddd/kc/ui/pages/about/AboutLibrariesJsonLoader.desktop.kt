package ddd.kc.ui.pages.about

private val aboutLibrariesResourceCandidates =
    listOf("files/aboutlibraries.json", "aboutlibraries.json")

internal actual suspend fun loadPlatformAboutLibrariesJsonOrNull(): String? {
  val contextClassLoader = Thread.currentThread().contextClassLoader
  return aboutLibrariesResourceCandidates.firstNotNullOfOrNull { candidate ->
    runCatching {
          val stream =
              contextClassLoader?.getResourceAsStream(candidate)
                  ?: object {}.javaClass.getResourceAsStream("/$candidate")
          stream?.bufferedReader()?.use { it.readText() }?.takeIf { it.isNotBlank() }
        }
        .getOrNull()
  }
}
