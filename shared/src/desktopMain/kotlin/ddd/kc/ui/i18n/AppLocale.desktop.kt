package ddd.kc.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import java.util.Locale

@Composable
actual fun ProvideAppLocale(localeTag: String?, content: @Composable () -> Unit) {
  DisposableEffect(localeTag) {
    val previous = Locale.getDefault()
    if (localeTag != null) Locale.setDefault(Locale.forLanguageTag(localeTag))
    onDispose { Locale.setDefault(previous) }
  }
  key(localeTag) { content() }
}
