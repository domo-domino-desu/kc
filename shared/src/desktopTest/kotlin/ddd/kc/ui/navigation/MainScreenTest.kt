package ddd.kc.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MainScreenTest {
  @Test
  fun selectingDifferentTabSelectsTab() {
    assertEquals(
        MainTabSelectionAction.SelectTab,
        resolveMainTabSelectionAction(targetTab = MainTab.Works, selectedTab = MainTab.Creators),
    )
  }

  @Test
  fun selectingCurrentTabRepeatsCurrentTab() {
    assertEquals(
        MainTabSelectionAction.RepeatCurrentTab,
        resolveMainTabSelectionAction(targetTab = MainTab.Dm, selectedTab = MainTab.Dm),
    )
  }

  @Test
  fun pawchivePwLinksResolveToInternalRoutes() {
    assertNotNull(
        parseKcRouteTarget(
            "https://pawchive.pw/patreon/user/artist/post/post1",
            NavigationWindowStore(),
        )
    )
  }
}
