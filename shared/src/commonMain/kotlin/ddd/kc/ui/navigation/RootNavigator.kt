package ddd.kc.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun RootNavigator(externalKcLinkEvents: Flow<String> = emptyFlow()) {
  Navigator(screen = MainScreen()) { navigator ->
    val defaultUriHandler = LocalUriHandler.current
    val kcLinkUriHandler =
        remember(navigator, defaultUriHandler) {
          KcLinkUriHandler(navigator = navigator, fallback = defaultUriHandler)
        }
    LaunchedEffect(externalKcLinkEvents, kcLinkUriHandler) {
      externalKcLinkEvents.collect { uri -> kcLinkUriHandler.openUri(uri) }
    }
    CompositionLocalProvider(LocalUriHandler provides kcLinkUriHandler) { CurrentScreen() }
  }
}
