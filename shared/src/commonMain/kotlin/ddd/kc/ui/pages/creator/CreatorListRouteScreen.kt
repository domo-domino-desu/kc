package ddd.kc.ui.pages.creator

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.ui.components.BackAppBar
import ddd.kc.ui.components.CreatorSearchCard
import ddd.kc.ui.navigation.nextRouteInstanceKey

class CreatorListRouteScreen(
    private val title: String,
    private val platform: Platform,
    private val creators: List<Creator>,
    private val showFavoriteCount: Boolean,
    private val routeKey: String = nextRouteInstanceKey("creator-list"),
) : Screen {
  override val key: String = routeKey

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    Scaffold(topBar = { BackAppBar(title) }) { paddingValues ->
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
              platform = platform,
              showFavoriteCount = showFavoriteCount,
              onClick = { navigator.push(CreatorRouteScreen(platform, creators, index)) },
          )
        }
      }
    }
  }
}
