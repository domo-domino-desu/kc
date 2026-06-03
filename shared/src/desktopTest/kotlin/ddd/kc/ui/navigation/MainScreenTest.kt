package ddd.kc.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
