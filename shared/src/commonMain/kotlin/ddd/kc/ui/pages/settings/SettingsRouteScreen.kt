package ddd.kc.ui.pages.settings

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.settings.AppSettings
import org.koin.compose.koinInject

class SettingsRouteScreen : Screen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val appSettings = koinInject<AppSettings>()
    SettingsScreen(
        appSettings = appSettings,
        onBack = { navigator.pop() },
        onGoHome = { navigator.popUntilRoot() },
    )
  }
}
