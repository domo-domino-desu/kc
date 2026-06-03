package ddd.kc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import dev.zwander.compose.rememberThemeInfo

@Composable
internal actual fun rememberPlatformColorScheme(forceDarkMode: Boolean?): ColorScheme {
  val isDarkMode = forceDarkMode ?: isSystemInDarkTheme()
  return rememberThemeInfo(isDarkMode = isDarkMode).colors
}
