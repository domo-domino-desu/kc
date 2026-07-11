package ddd.kc.ui.app.i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
actual fun ProvideAppLocale(localeTag: String?, content: @Composable () -> Unit) {
  val configuration = Configuration(LocalConfiguration.current)
  localeTag?.let { configuration.setLocale(Locale.forLanguageTag(it)) }
  CompositionLocalProvider(LocalConfiguration provides configuration) {
    key(localeTag) { content() }
  }
}
