package ddd.kc.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MainScreenTest {
  @Test
  fun selectingDifferentTabSelectsTab() {
    assertEquals(
        MainTabSelectionAction.SelectTab,
        resolveMainTabSelectionAction(targetTab = 1, selectedTab = 0),
    )
  }

  @Test
  fun selectingCurrentTabRepeatsCurrentTab() {
    assertEquals(
        MainTabSelectionAction.RepeatCurrentTab,
        resolveMainTabSelectionAction(targetTab = 2, selectedTab = 2),
    )
  }

  @Test
  fun pawchivePwLinksResolveToInternalRoutes() {
    assertNotNull(parseKcRouteTarget("https://pawchive.pw/patreon/user/artist/post/post1"))
  }
}
