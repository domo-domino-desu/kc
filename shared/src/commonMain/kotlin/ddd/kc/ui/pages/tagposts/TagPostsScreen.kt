package ddd.kc.ui.pages.tagposts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Platform
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.GridLoadingSkeleton
import ddd.kc.ui.components.PostCard
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.post.PostRouteScreen
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import org.koin.core.parameter.parametersOf

private val log = KcLog.withTag("TagPostsScreen")

class TagPostsScreen(
    private val platform: Platform,
    private val tag: String,
    private val routeKey: String = nextRouteInstanceKey("tag-posts"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<TagPostsScreenModel> { parametersOf(tag) }
    val state by screenModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    val cellWidth = LocalAppSettings.current.cellMinWidthDp()

    LaunchedEffect(Unit) { screenModel.load(platform) }
    AutoLoadEffect(
        gridState,
        state.items.size,
        hasMore = state.hasMore,
        isLoadingMore = state.isLoadingMore,
    ) {
      screenModel.loadMore(platform)
    }
    ErrorToastEffect(state.errorMessage)
    ErrorToastEffect(state.appendErrorMessage)

    Scaffold(topBar = { DetailAppBar("#$tag") }) { paddingValues ->
      if (state.loading && state.items.isEmpty()) {
        GridLoadingSkeleton(
            minCardWidthDp = cellWidth,
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
      } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cellWidth.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.items, key = { it.id }) { post ->
            PostCard(
                post = post,
                platform = platform,
                onClick = {
                  log.i {
                    "打开Post -> 点击来源(source=tag,platform=${platform.name},${summarizePost(post)})"
                  }
                  navigator.push(
                      PostRouteScreen(
                          platform,
                          state.items,
                          state.items.indexOf(post),
                          source = "tag",
                      )
                  )
                },
            )
          }
          loadingFooter(state.isLoadingMore, state.appendErrorMessage)
        }
      }
    }
  }
}
