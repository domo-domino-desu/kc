package ddd.kc.ui.pages.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Platform
import ddd.kc.ui.components.BackAppBar
import ddd.kc.ui.components.CreatorSearchCard
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.GridLoadingSkeleton
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.PostCard
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.post.PostRouteScreen
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.favorites
import kc.shared.generated.resources.favorites_tab_creators
import kc.shared.generated.resources.favorites_tab_posts
import org.jetbrains.compose.resources.stringResource

class FavoritesScreen(
    private val platform: Platform = Platform.KEMONO,
    private val routeKey: String = nextRouteInstanceKey("favorites"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<FavoritesScreenModel>()
    val state by screenModel.state.collectAsState()
    val cellWidth = LocalAppSettings.current.cellMinWidthDp()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabLabels =
        listOf(
            stringResource(Res.string.favorites_tab_creators),
            stringResource(Res.string.favorites_tab_posts),
        )

    LaunchedEffect(platform) { screenModel.load(platform) }
    ErrorToastEffect(state.errorMessage)

    Scaffold(topBar = { BackAppBar(stringResource(Res.string.favorites)) }) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          tabLabels.forEachIndexed { index, label ->
            toggleableItem(
                checked = selectedTab == index,
                label = label,
                onCheckedChange = { selectedTab = index },
                weight = 1f,
            )
          }
        }

        when (selectedTab) {
          0 ->
              if (state.isLoading) {
                ListLoadingSkeleton(modifier = Modifier.padding(horizontal = 12.dp))
              } else
                  LazyVerticalGrid(
                      columns = GridCells.Adaptive(minSize = 320.dp),
                      modifier = Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(12.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    items(state.creators, key = { it.id }) { creator ->
                      CreatorSearchCard(
                          creator = creator,
                          platform = platform,
                          onClick = {
                            navigator.push(
                                CreatorRouteScreen(
                                    platform,
                                    state.creators,
                                    state.creators.indexOf(creator),
                                )
                            )
                          },
                      )
                    }
                  }
          1 ->
              if (state.isLoading) {
                GridLoadingSkeleton(minCardWidthDp = cellWidth)
              } else
                  LazyVerticalGrid(
                      columns = GridCells.Adaptive(minSize = cellWidth.dp),
                      modifier = Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(12.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    items(state.posts, key = { it.id }) { post ->
                      PostCard(
                          post = post,
                          platform = platform,
                          onClick = {
                            navigator.push(
                                PostRouteScreen(
                                    platform,
                                    state.posts,
                                    state.posts.indexOf(post),
                                    source = "favorites",
                                )
                            )
                          },
                      )
                    }
                  }
        }
      }
    }
  }
}
