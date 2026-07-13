package ddd.kc.ui.app.navigation

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
  val navigationWindows = remember { NavigationWindowStore() }
  val tabNavigation = remember { KcTabNavigationController() }
  Navigator(screen = MainScreen()) { navigator ->
    val defaultUriHandler = LocalUriHandler.current
    val kcLinkUriHandler =
        remember(tabNavigation, defaultUriHandler) {
          KcLinkUriHandler(
              openInternal = tabNavigation::pushCurrent,
              fallback = defaultUriHandler,
              navigationWindows = navigationWindows,
          )
        }
    LaunchedEffect(externalKcLinkEvents, kcLinkUriHandler) {
      externalKcLinkEvents.collect { uri ->
        val target = parseKcRouteTarget(uri, navigationWindows)
        if (target == null) {
          defaultUriHandler.openUri(uri)
        } else {
          val tab =
              if (target is ddd.kc.ui.pages.creator.CreatorRouteScreen) MainTab.Creators
              else MainTab.Works
          tabNavigation.openExternal(tab, target)
        }
      }
    }
    CompositionLocalProvider(
        LocalUriHandler provides kcLinkUriHandler,
        LocalNavigationWindowStore provides navigationWindows,
        LocalKcTabNavigationController provides tabNavigation,
    ) {
      CurrentScreen()
    }
  }
}
