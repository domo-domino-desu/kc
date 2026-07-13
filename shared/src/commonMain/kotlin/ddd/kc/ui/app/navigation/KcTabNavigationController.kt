package ddd.kc.ui.app.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator

internal class KcTabNavigationController {
  var selectedTab by mutableStateOf(MainTab.Creators)
    private set

  private val navigators = mutableMapOf<MainTab, Navigator>()
  private val pending = mutableMapOf<MainTab, MutableList<AppScreen>>()
  private val depths = mutableStateMapOf<MainTab, Int>()

  val currentDepth: Int
    get() = depths[selectedTab] ?: 1

  fun select(tab: MainTab) {
    selectedTab = tab
  }

  fun register(tab: MainTab, navigator: Navigator) {
    navigators[tab] = navigator
    depths[tab] = navigator.size
    pending.remove(tab)?.forEach(navigator::push)
  }

  fun updateDepth(tab: MainTab, depth: Int) {
    depths[tab] = depth
  }

  fun pushCurrent(screen: AppScreen) {
    navigators[selectedTab]?.push(screen)
        ?: pending.getOrPut(selectedTab, ::mutableListOf).add(screen)
  }

  fun openExternal(tab: MainTab, screen: AppScreen) {
    selectedTab = tab
    navigators[tab]?.push(screen) ?: pending.getOrPut(tab, ::mutableListOf).add(screen)
  }
}

internal val LocalKcTabNavigationController =
    compositionLocalOf<KcTabNavigationController> { error("KcTabNavigationController missing") }
