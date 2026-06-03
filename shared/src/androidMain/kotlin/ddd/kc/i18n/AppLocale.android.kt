package ddd.kc.i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
  private var default: Locale? = null

  actual val current: String
    @Composable get() = Locale.getDefault().toLanguageTag()

  @Composable
  actual infix fun provides(value: String?): ProvidedValue<*> {
    val configuration = Configuration(LocalConfiguration.current)
    if (default == null) {
      default = Locale.getDefault()
    }
    val newLocale =
        when (value) {
          null -> default!!
          else -> Locale.forLanguageTag(value)
        }
    Locale.setDefault(newLocale)
    configuration.setLocale(newLocale)
    return LocalConfiguration.provides(configuration)
  }
}
