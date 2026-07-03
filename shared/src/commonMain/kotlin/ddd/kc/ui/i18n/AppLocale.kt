package ddd.kc.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

expect object LocalAppLocale {
  val current: String
    @Composable get

  @Composable infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
fun ProvideAppLocale(
    localeTag: String?,
    content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalAppLocale provides localeTag) { key(localeTag) { content() } }
}
