package ddd.kc.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
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

private val navRailMinWidth = 600.dp

private data class TabDef(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

internal enum class MainTabSelectionAction {
  SelectTab,
  RepeatCurrentTab,
}

internal fun resolveMainTabSelectionAction(
    targetTab: Int,
    selectedTab: Int,
): MainTabSelectionAction =
    if (targetTab == selectedTab) MainTabSelectionAction.RepeatCurrentTab
    else MainTabSelectionAction.SelectTab

@Composable
private fun tabs() =
    listOf(
        TabDef(
            stringResource(Res.string.tab_creators),
            Icons.PersonW400Outlined,
            Icons.PersonW400Outlinedfill1,
        ),
        TabDef(
            stringResource(Res.string.tab_works),
            Icons.ImageW400Outlined,
            Icons.ImageW400Outlinedfill1,
        ),
        TabDef(
            stringResource(Res.string.tab_dm),
            Icons.CommentW400Outlined,
            Icons.CommentW400Outlinedfill1,
        ),
        TabDef(
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var creatorsRepeatToken by rememberSaveable { mutableIntStateOf(0) }
    var worksRepeatToken by rememberSaveable { mutableIntStateOf(0) }
    var dmRepeatToken by rememberSaveable { mutableIntStateOf(0) }
    var moreRepeatToken by rememberSaveable { mutableIntStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()
    val repeatTokenForSelectedTab =
        when (selectedTab) {
          0 -> creatorsRepeatToken
          1 -> worksRepeatToken
          2 -> dmRepeatToken
          else -> moreRepeatToken
        }
    val onTabSelected: (Int) -> Unit = { targetTab ->
      when (resolveMainTabSelectionAction(targetTab, selectedTab)) {
        MainTabSelectionAction.SelectTab -> selectedTab = targetTab
        MainTabSelectionAction.RepeatCurrentTab ->
            when (targetTab) {
              0 -> creatorsRepeatToken += 1
              1 -> worksRepeatToken += 1
              2 -> dmRepeatToken += 1
              3 -> moreRepeatToken += 1
            }
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      if (maxWidth >= navRailMinWidth) {
        RailLayout(
            selectedTab = selectedTab,
            repeatSelectionToken = repeatTokenForSelectedTab,
            onTabSelected = onTabSelected,
            stateHolder = stateHolder,
        )
      } else {
        BottomBarLayout(
            selectedTab = selectedTab,
            repeatSelectionToken = repeatTokenForSelectedTab,
            onTabSelected = onTabSelected,
            stateHolder = stateHolder,
        )
      }
    }
  }
}

@Composable
private fun BottomBarLayout(
    selectedTab: Int,
    repeatSelectionToken: Int,
    onTabSelected: (Int) -> Unit,
    stateHolder: SaveableStateHolder,
) {
  val tabList = tabs()
  Scaffold(
      containerColor = MaterialTheme.colorScheme.surface,
      contentWindowInsets = WindowInsets(0.dp),
      bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
          tabList.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                  Icon(
                      imageVector = if (selectedTab == index) tab.selectedIcon else tab.icon,
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
      TabContent(selectedTab, repeatSelectionToken, stateHolder)
    }
  }
}

@Composable
private fun RailLayout(
    selectedTab: Int,
    repeatSelectionToken: Int,
    onTabSelected: (Int) -> Unit,
    stateHolder: SaveableStateHolder,
) {
  val tabList = tabs()
  Row(modifier = Modifier.fillMaxSize()) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
      tabList.forEachIndexed { index, tab ->
        NavigationRailItem(
            selected = selectedTab == index,
            onClick = { onTabSelected(index) },
            icon = {
              Icon(
                  imageVector = if (selectedTab == index) tab.selectedIcon else tab.icon,
                  contentDescription = tab.label,
              )
            },
            label = { Text(tab.label) },
        )
      }
    }
    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
      TabContent(selectedTab, repeatSelectionToken, stateHolder)
    }
  }
}

@Composable
private fun TabContent(
    selectedTab: Int,
    repeatSelectionToken: Int,
    stateHolder: SaveableStateHolder,
) {
  // SaveableStateProvider preserves each tab's rememberSaveable state (scroll pos etc.)
  // across tab switches and root navigator pushes.
  stateHolder.SaveableStateProvider(key = selectedTab) {
    when (selectedTab) {
      0 -> CreatorsScreen(repeatSelectionToken).Content()
      1 -> WorksScreen(repeatSelectionToken).Content()
      2 -> DmScreen(repeatSelectionToken).Content()
      3 -> MoreScreen(repeatSelectionToken).Content()
    }
  }
}
