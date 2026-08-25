package ddd.kc.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.local.settings.SettingsLoadState
import ddd.kc.ui.app.challenge.PawchiveChallengeOverlayHost
import ddd.kc.ui.app.i18n.ProvideAppLocale
import ddd.kc.ui.app.navigation.RootNavigator
import ddd.kc.ui.components.AppFeedbackHost
import ddd.kc.ui.theme.ProvideKcTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.koinInject

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }

@Composable
fun KcApp(externalKcLinkEvents: Flow<String> = emptyFlow()) {
  KcAppEnvironment { settingsState ->
    KcAppContent(settingsState = settingsState, externalKcLinkEvents = externalKcLinkEvents)
  }
}

/** 提供设置、语言与主题，使桌面窗口装饰和应用内容共享同一套环境。 */
@Composable
fun KcAppEnvironment(content: @Composable (SettingsLoadState) -> Unit) {
  val appSettings = koinInject<AppSettings>()
  LaunchedEffect(Unit) { appSettings.init() }
  val settingsState by appSettings.loadState.collectAsState()
  val language by appSettings.languageFlow().collectAsState(appSettings.language())
  val themeMode by appSettings.themeModeFlow().collectAsState(appSettings.themeMode())
  CompositionLocalProvider(
      LocalAppSettings provides appSettings,
  ) {
    ProvideAppLocale(localeTag = language.resourceLocaleTag) {
      ProvideKcTheme(themeMode = themeMode) { content(settingsState) }
    }
  }
}

/** 应用窗口内的内容；调用方需要先提供 [KcAppEnvironment]。 */
@Composable
fun KcAppContent(
    settingsState: SettingsLoadState,
    externalKcLinkEvents: Flow<String> = emptyFlow(),
) {
  Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
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
          AppFeedbackHost {
            Box(modifier = Modifier.fillMaxSize()) {
              RootNavigator(externalKcLinkEvents = externalKcLinkEvents)
              PawchiveChallengeOverlayHost(modifier = Modifier.fillMaxSize())
            }
          }
    }
  }
}
