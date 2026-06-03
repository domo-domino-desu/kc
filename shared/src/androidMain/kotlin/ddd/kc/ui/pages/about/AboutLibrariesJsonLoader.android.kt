package ddd.kc.ui.pages.about

private const val aboutLibrariesComposeAssetPath =
    "composeResources/kc.shared.generated.resources/files/aboutlibraries.json"

internal actual suspend fun loadPlatformAboutLibrariesJsonOrNull(): String? {
  val appContext = AboutLibrariesAndroidContextHolder.context ?: return null

  return runCatching {
        appContext.assets.open(aboutLibrariesComposeAssetPath).bufferedReader().use {
          it.readText()
        }
      }
      .getOrNull()
}
