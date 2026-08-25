package ddd.kc.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ddd.kc.data.local.settings.ThemeMode

@Composable
fun KcTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
  ProvideKcTheme(themeMode = themeMode) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
  }
}

/** 只提供应用主题，不绘制界面节点；供桌面窗口装饰与应用内容共享配色。 */
@Composable
fun ProvideKcTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
  val forceDarkMode =
      when (themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

  MaterialTheme(
      colorScheme = rememberPlatformColorScheme(forceDarkMode),
      content = content,
  )
}

@Composable internal expect fun rememberPlatformColorScheme(forceDarkMode: Boolean?): ColorScheme
