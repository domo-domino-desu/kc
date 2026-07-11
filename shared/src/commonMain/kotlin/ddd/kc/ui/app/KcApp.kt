package ddd.kc.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ddd.kc.data.settings.AppSettings
import ddd.kc.data.settings.SettingsLoadState
import ddd.kc.ui.components.AppFeedbackHost
import ddd.kc.ui.i18n.ProvideAppLocale
import ddd.kc.ui.navigation.RootNavigator
import ddd.kc.ui.theme.KcTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }

@Composable
fun KcApp(externalKcLinkEvents: Flow<String> = emptyFlow()) {
  KoinContext {
    val appSettings = koinInject<AppSettings>()
    LaunchedEffect(Unit) { appSettings.init() }
    val settingsState by appSettings.loadState.collectAsState()
    val language by appSettings.languageFlow().collectAsState(appSettings.language())
    val themeMode by appSettings.themeModeFlow().collectAsState(appSettings.themeMode())
    CompositionLocalProvider(
        LocalAppSettings provides appSettings,
    ) {
      ProvideAppLocale(localeTag = language.resourceLocaleTag) {
        KcTheme(themeMode = themeMode) {
          when (val state = settingsState) {
            SettingsLoadState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
            is SettingsLoadState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  Text(state.cause.message ?: "Settings initialization failed")
                }
            is SettingsLoadState.Ready ->
                AppFeedbackHost { RootNavigator(externalKcLinkEvents = externalKcLinkEvents) }
          }
        }
      }
    }
  }
}
