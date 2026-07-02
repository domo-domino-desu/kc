package ddd.kc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.network.PageInfo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class PostGridPagingState(
    val posts: List<Post>,
    val visiblePageInfo: PageInfo?,
    val loading: Boolean,
    val refreshing: Boolean,
    val isLoadingMore: Boolean,
    val isLoadingPrevious: Boolean,
    val hasMore: Boolean,
    val canLoadPrevious: Boolean,
    val appendErrorMessage: String? = null,
)

data class PostGridPagingActions(
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onLoadPrevious: () -> Unit,
    val onJumpToPage: (Int) -> Unit,
    val onVisiblePostIndex: (Int) -> Unit,
    val onPostClick: (Post) -> Unit,
)

@Composable
fun PagedPostGrid(
    platform: Platform,
    state: PostGridPagingState,
    actions: PostGridPagingActions,
    gridState: LazyGridState,
    minCardWidth: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    leadingItemCount: Int = 0,
    leadingContent: LazyGridScope.() -> Unit = {},
) {
  Box(modifier = modifier) {
    KcPullRefreshBox(
        enabled = !state.loading,
        refreshing = state.refreshing,
        onRefresh = actions.onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
      LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = minCardWidth),
          state = gridState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = contentPadding,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        leadingContent()
        if (state.loading && state.posts.isEmpty()) {
          gridSkeletonItems()
        } else {
          items(state.posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                platform = platform,
                onClick = { actions.onPostClick(post) },
            )
          }
          loadingFooter(state.isLoadingMore, state.appendErrorMessage)
        }
      }
    }
    PostGridPagingControls(
        state = state,
        actions = actions,
        gridState = gridState,
        leadingItemCount = leadingItemCount,
    )
  }
}

@Composable
fun BoxScope.PostGridPagingControls(
    state: PostGridPagingState,
    actions: PostGridPagingActions,
    gridState: LazyGridState,
    leadingItemCount: Int = 0,
) {
  val scope = rememberCoroutineScope()
  LaunchedEffect(gridState, leadingItemCount) {
    snapshotFlow { gridState.firstVisibleItemIndex }
        .distinctUntilChanged()
        .collect { index -> actions.onVisiblePostIndex(index - leadingItemCount) }
  }
  AutoLoadEffect(
      gridState,
      state.posts.size,
      hasMore = state.hasMore,
      isLoadingMore = state.isLoadingMore,
      onLoadMore = actions.onLoadMore,
  )
  AutoLoadPreviousEffect(
      gridState,
      state.posts.size,
      hasPrevious = state.canLoadPrevious,
      isLoadingPrevious = state.isLoadingPrevious,
      onLoadPrevious = actions.onLoadPrevious,
  )
  PageJumpFabMenu(
      pageInfo = state.visiblePageInfo,
      loading = state.loading || state.isLoadingMore || state.isLoadingPrevious,
      onJumpToPage = { page ->
        actions.onJumpToPage(page)
        scope.launch { gridState.scrollToItem(0) }
      },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
  )
}

fun LazyGridScope.fullWidthItem(
    key: Any? = null,
    content: @Composable () -> Unit,
) {
  item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
