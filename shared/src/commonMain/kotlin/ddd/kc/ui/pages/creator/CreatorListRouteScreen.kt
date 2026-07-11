package ddd.kc.ui.pages.creator

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.key
import ddd.kc.ui.app.navigation.LocalNavigationWindowStore
import ddd.kc.ui.app.navigation.nextRouteInstanceKey
import ddd.kc.ui.components.BackAppBar
import ddd.kc.ui.components.CreatorSearchCard
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.navigation_content_expired
import org.jetbrains.compose.resources.stringResource

class CreatorListRouteScreen(
    private val title: String,
    private val windowId: String,
    private val showFavoriteCount: Boolean,
    private val routeKey: String = nextRouteInstanceKey("creator-list"),
) : Screen {
  override val key: String = routeKey

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val navigationWindows = LocalNavigationWindowStore.current
    val creators = navigationWindows.creators(windowId)
    Scaffold(topBar = { BackAppBar(title) }) { paddingValues ->
      if (creators == null) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
          Text(stringResource(Res.string.navigation_content_expired))
        }
        return@Scaffold
      }
      LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 320.dp),
          modifier = Modifier.fillMaxSize().padding(paddingValues),
      ) {
        itemsIndexed(
            items = creators,
            key = { index, creator -> "${creator.service}:${creator.id}:$index" },
        ) { index, creator ->
          CreatorSearchCard(
              creator = creator,
              showFavoriteCount = showFavoriteCount,
              onClick = {
                navigator.push(
                    CreatorRouteScreen(
                        navigationWindows.putCreators(creators),
                        creator.key,
                        index,
                    )
                )
              },
          )
        }
      }
    }
  }
}
