package ddd.kc.ui.pages.settings

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.ui.app.navigation.AppScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
class SettingsRouteScreen : AppScreen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val appSettings = koinInject<AppSettings>()
    val screenModel = koinScreenModel<SettingsScreenModel>()
    SettingsScreen(
        appSettings = appSettings,
        screenModel = screenModel,
        onBack = { navigator.pop() },
        onGoHome = { navigator.popUntilRoot() },
    )
  }
}
