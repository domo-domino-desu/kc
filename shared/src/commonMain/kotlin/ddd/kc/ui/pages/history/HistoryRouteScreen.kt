package ddd.kc.ui.pages.history

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.creatorId
import ddd.kc.data.repository.ActivityHistoryRepository
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.components.BackAppBar
import ddd.kc.ui.components.CreatorSearchCard
import ddd.kc.ui.components.PostCard
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.post.PostRouteScreen
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.empty_history
import kc.shared.generated.resources.history
import kc.shared.generated.resources.history_tab_posts
import kc.shared.generated.resources.history_tab_users
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class HistoryRouteScreen(
    private val platform: Platform = Platform.PAWCHIVE,
    private val routeKey: String = nextRouteInstanceKey("history"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val historyRepository = koinInject<ActivityHistoryRepository>()
    val cellWidth = LocalAppSettings.current.cellMinWidthDp()
    var selectedTab by remember { mutableIntStateOf(0) }
    var creators by remember { mutableStateOf<List<Creator>>(emptyList()) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    val tabLabels =
        listOf(
            stringResource(Res.string.history_tab_users),
            stringResource(Res.string.history_tab_posts),
        )

    LaunchedEffect(Unit) {
      creators = historyRepository.loadCreatorHistory()
      posts = historyRepository.loadPostHistory()
    }

    Scaffold(topBar = { BackAppBar(stringResource(Res.string.history)) }) { paddingValues ->
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
              if (creators.isEmpty()) {
                EmptyHistory()
              } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(creators, key = { "${it.service}:${it.id}" }) { creator ->
                    CreatorSearchCard(
                        creator = creator,
                        platform = platform,
                        onClick = {
                          navigator.push(
                              CreatorRouteScreen(
                                  platform = platform,
                                  creators = creators,
                                  startIndex = creators.indexOf(creator),
                              )
                          )
                        },
                    )
                  }
                }
              }
          else ->
              if (posts.isEmpty()) {
                EmptyHistory()
              } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = cellWidth.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(posts, key = { "${it.service}:${it.creatorId}:${it.id}" }) { post ->
                    PostCard(
                        post = post,
                        platform = platform,
                        onClick = {
                          navigator.push(
                              PostRouteScreen(
                                  platform = platform,
                                  posts = posts,
                                  startIndex = posts.indexOf(post),
                                  source = "history",
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
}

@Composable
private fun EmptyHistory() {
  Text(
      text = stringResource(Res.string.empty_history),
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
  )
}
