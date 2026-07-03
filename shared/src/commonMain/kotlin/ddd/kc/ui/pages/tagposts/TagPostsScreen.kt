package ddd.kc.ui.pages.tagposts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import ddd.kc.data.model.Platform
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.PagedPostGrid
import ddd.kc.ui.components.PostGridPagingActions
import ddd.kc.ui.components.PostGridPagingState
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.post.PostPagingContext
import ddd.kc.ui.pages.post.PostRouteScreen
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
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
    val refresh = { screenModel.load(platform, forceRefresh = true) }

    LaunchedEffect(Unit) { screenModel.load(platform) }
    ErrorToastEffect(state.errorMessage)
    ErrorToastEffect(state.appendErrorMessage)
    ErrorToastEffect(state.prependErrorMessage)

    Scaffold(topBar = { DetailAppBar("#$tag") }) { paddingValues ->
      Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        PagedPostGrid(
            platform = platform,
            state =
                PostGridPagingState(
                    posts = state.items,
                    visiblePageInfo = state.visiblePageInfo,
                    loading = state.loading,
                    refreshing = state.refreshing,
                    isLoadingMore = state.isLoadingMore,
                    isLoadingPrevious = state.isLoadingPrevious,
                    hasMore = state.hasMore,
                    canLoadPrevious = state.canAutoLoadPrevious,
                    appendErrorMessage = state.appendErrorMessage,
                ),
            actions =
                PostGridPagingActions(
                    onRefresh = refresh,
                    onLoadMore = { screenModel.loadMore(platform) },
                    onLoadPrevious = { screenModel.loadPrevious(platform) },
                    onJumpToPage = { page -> screenModel.jumpToPage(platform, page) },
                    onVisiblePostIndex = screenModel::onVisiblePostIndex,
                    onPostClick = { post ->
                      log.i {
                        "打开Post -> 点击来源(source=tag,platform=${platform.name},${summarizePost(post)})"
                      }
                      navigator.push(
                          PostRouteScreen(
                              platform = platform,
                              posts = state.items,
                              startIndex = state.items.indexOf(post),
                              source = "tag",
                              initialOffset = state.startOffset,
                              initialHasMore = state.hasMore,
                              pagingContext = PostPagingContext.Tag(tag),
                          )
                      )
                    },
                ),
            gridState = gridState,
            minCardWidth = cellWidth.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
        )
      }
    }
  }
}
