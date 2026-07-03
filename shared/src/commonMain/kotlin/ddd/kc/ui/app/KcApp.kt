package ddd.kc.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import ddd.kc.data.model.Platform
import ddd.kc.data.settings.AppSettings
import ddd.kc.ui.components.AppFeedbackHost
import ddd.kc.ui.i18n.ProvideAppLocale
import ddd.kc.ui.navigation.RootNavigator
import ddd.kc.ui.theme.KcTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
val LocalActivePlatform = staticCompositionLocalOf<Platform> { Platform.PAWCHIVE }

@Composable
fun KcApp(externalKcLinkEvents: Flow<String> = emptyFlow()) {
  KoinContext {
    val appSettings = koinInject<AppSettings>()
    LaunchedEffect(Unit) { appSettings.init() }
    val language by appSettings.languageFlow().collectAsState(appSettings.language())
    val activePlatform by appSettings.activePlatformFlow.collectAsState()
    val themeMode by appSettings.themeModeFlow().collectAsState(appSettings.themeMode())
    CompositionLocalProvider(
        LocalAppSettings provides appSettings,
        LocalActivePlatform provides activePlatform,
    ) {
      ProvideAppLocale(localeTag = language.resourceLocaleTag) {
        KcTheme(themeMode = themeMode) {
          AppFeedbackHost { RootNavigator(externalKcLinkEvents = externalKcLinkEvents) }
        }
      }
    }
  }
}
