package ddd.kc.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CommentW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CommentW400Outlinedfill1
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ImageW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ImageW400Outlinedfill1
import ddd.kc.generated.symbols.icons.materialsymbols.icons.MenuW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.MenuW400Outlinedfill1
import ddd.kc.generated.symbols.icons.materialsymbols.icons.PersonW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.PersonW400Outlinedfill1
import ddd.kc.ui.pages.creators.CreatorsScreen
import ddd.kc.ui.pages.dm.DmScreen
import ddd.kc.ui.pages.more.MoreScreen
import ddd.kc.ui.pages.works.WorksScreen
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.tab_creators
import kc.shared.generated.resources.tab_dm
import kc.shared.generated.resources.tab_more
import kc.shared.generated.resources.tab_works
import org.jetbrains.compose.resources.stringResource

private val navRailMinWidth = 960.dp

private data class TabDef(
    val key: MainTab,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

internal enum class MainTabSelectionAction {
  SelectTab,
  RepeatCurrentTab,
}

internal enum class MainTab {
  Creators,
  Works,
  Dm,
  More,
}

internal fun resolveMainTabSelectionAction(
    targetTab: MainTab,
    selectedTab: MainTab,
): MainTabSelectionAction =
    if (targetTab == selectedTab) MainTabSelectionAction.RepeatCurrentTab
    else MainTabSelectionAction.SelectTab

@Composable
private fun tabs() =
    listOf(
        TabDef(
            MainTab.Creators,
            stringResource(Res.string.tab_creators),
            Icons.PersonW400Outlined,
            Icons.PersonW400Outlinedfill1,
        ),
        TabDef(
            MainTab.Works,
            stringResource(Res.string.tab_works),
            Icons.ImageW400Outlined,
            Icons.ImageW400Outlinedfill1,
        ),
        TabDef(
            MainTab.Dm,
            stringResource(Res.string.tab_dm),
            Icons.CommentW400Outlined,
            Icons.CommentW400Outlinedfill1,
        ),
        TabDef(
            MainTab.More,
            stringResource(Res.string.tab_more),
            Icons.MenuW400Outlined,
            Icons.MenuW400Outlinedfill1,
        ),
    )

/**
 * Single root screen. Tab content is rendered inline; detail screens push onto the root navigator.
 */
class MainScreen : Screen {
  @Composable
  override fun Content() {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.Creators.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)
    var currentTabReselectHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val onReselectHandlerChanged = remember {
      { handler: (() -> Unit)? -> currentTabReselectHandler = handler }
    }
    val stateHolder = rememberSaveableStateHolder()
    val onTabSelected: (MainTab) -> Unit = { targetTab ->
      when (resolveMainTabSelectionAction(targetTab, selectedTab)) {
        MainTabSelectionAction.SelectTab -> {
          currentTabReselectHandler = null
          selectedTabName = targetTab.name
        }
        MainTabSelectionAction.RepeatCurrentTab -> currentTabReselectHandler?.invoke()
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      if (maxWidth >= navRailMinWidth) {
        RailLayout(
            selectedTab = selectedTab,
            onReselectHandlerChanged = onReselectHandlerChanged,
            onTabSelected = onTabSelected,
            stateHolder = stateHolder,
        )
      } else {
        BottomBarLayout(
            selectedTab = selectedTab,
            onReselectHandlerChanged = onReselectHandlerChanged,
            onTabSelected = onTabSelected,
            stateHolder = stateHolder,
        )
      }
    }
  }
}

@Composable
private fun BottomBarLayout(
    selectedTab: MainTab,
    onReselectHandlerChanged: ((() -> Unit)?) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    stateHolder: SaveableStateHolder,
) {
  val tabList = tabs()
  Scaffold(
      containerColor = MaterialTheme.colorScheme.surface,
      bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
          tabList.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab.key,
                onClick = { onTabSelected(tab.key) },
                icon = {
                  Icon(
                      imageVector = if (selectedTab == tab.key) tab.selectedIcon else tab.icon,
                      contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
            )
          }
        }
      },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      TabContent(selectedTab, onReselectHandlerChanged, stateHolder)
    }
  }
}

@Composable
private fun RailLayout(
    selectedTab: MainTab,
    onReselectHandlerChanged: ((() -> Unit)?) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    stateHolder: SaveableStateHolder,
) {
  val tabList = tabs()
  Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
      tabList.forEach { tab ->
        NavigationRailItem(
            selected = selectedTab == tab.key,
            onClick = { onTabSelected(tab.key) },
            icon = {
              Icon(
                  imageVector = if (selectedTab == tab.key) tab.selectedIcon else tab.icon,
                  contentDescription = tab.label,
              )
            },
            label = { Text(tab.label) },
        )
      }
    }
    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
      TabContent(selectedTab, onReselectHandlerChanged, stateHolder)
    }
  }
}

@Composable
private fun TabContent(
    selectedTab: MainTab,
    onReselectHandlerChanged: ((() -> Unit)?) -> Unit,
    stateHolder: SaveableStateHolder,
) {
  // SaveableStateProvider preserves each tab's rememberSaveable state (scroll pos etc.)
  // across tab switches and root navigator pushes.
  stateHolder.SaveableStateProvider(key = selectedTab) {
    when (selectedTab) {
      MainTab.Creators -> Navigator(CreatorsScreen(onReselectHandlerChanged))
      MainTab.Works -> Navigator(WorksScreen(onReselectHandlerChanged))
      MainTab.Dm -> Navigator(DmScreen(onReselectHandlerChanged))
      MainTab.More -> Navigator(MoreScreen(onReselectHandlerChanged))
    }
  }
}
