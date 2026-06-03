package ddd.kc.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class ThemeMode(val persistedValue: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromPersistedValue(raw: String?): ThemeMode? =
        entries.firstOrNull { it.persistedValue == raw?.trim() }
  }
}

@Composable
fun KcTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
  val forceDarkMode =
      when (themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

  MaterialTheme(
      colorScheme = rememberPlatformColorScheme(forceDarkMode),
      content = {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
          content()
        }
      },
  )
}

@Composable internal expect fun rememberPlatformColorScheme(forceDarkMode: Boolean?): ColorScheme
