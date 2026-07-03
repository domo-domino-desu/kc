package ddd.kc.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
  private var default: Locale? = null
  private val localAppLocale = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

  actual val current: String
    @Composable get() = localAppLocale.current

  @Composable
  actual infix fun provides(value: String?): ProvidedValue<*> {
    if (default == null) {
      default = Locale.getDefault()
    }
    val newLocale =
        when (value) {
          null -> default!!
          else -> Locale.forLanguageTag(value)
        }
    Locale.setDefault(newLocale)
    return localAppLocale.provides(newLocale.toLanguageTag())
  }
}
