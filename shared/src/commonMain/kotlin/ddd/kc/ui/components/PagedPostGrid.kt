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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.Post
import ddd.kc.data.model.key
import ddd.kc.ui.components.paging.PagingAnchor
import ddd.kc.ui.components.paging.PagingEffect
import kotlinx.coroutines.flow.distinctUntilChanged

data class PostGridPagingState(
    val posts: List<Post>,
    val visiblePageInfo: PageInfo?,
    val loading: Boolean,
    val refreshing: Boolean,
    val isLoadingMore: Boolean,
    val isLoadingPrevious: Boolean,
    val hasMore: Boolean,
    val canLoadPrevious: Boolean,
    val prependErrorMessage: String? = null,
    val appendErrorMessage: String? = null,
    val navigationEffect: PagingEffect? = null,
)

data class PostGridPagingActions(
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onLoadPrevious: () -> Unit,
    val onJumpToPage: (Int) -> Unit,
    val onViewportChanged: (PagingAnchor) -> Unit,
    val onPostClick: (Post) -> Unit,
)

@Composable
fun PagedPostGrid(
    state: PostGridPagingState,
    actions: PostGridPagingActions,
    gridState: LazyGridState,
    minCardWidth: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    leadingItemCount: Int = 0,
    leadingContent: LazyGridScope.() -> Unit = {},
    initialLoadingContent: LazyGridScope.() -> Unit = { gridSkeletonItems() },
    emptyContent: LazyGridScope.() -> Unit = {},
) {
  Box(modifier = modifier) {
    PagedPullRefreshBox(
        currentPage = state.visiblePageInfo?.currentPage ?: 1,
        enabled = !state.loading,
        refreshing = state.refreshing,
        loadingPrevious = state.isLoadingPrevious,
        onRefresh = actions.onRefresh,
        onLoadPrevious = actions.onLoadPrevious,
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
        previousPageHeader(
            canLoadPrevious = state.canLoadPrevious,
            loadingPrevious = state.isLoadingPrevious,
            errorMessage = state.prependErrorMessage,
            onLoadPrevious = actions.onLoadPrevious,
        )
        if (state.loading && state.posts.isEmpty()) {
          initialLoadingContent()
        } else if (state.posts.isEmpty()) {
          emptyContent()
        } else {
          items(state.posts, key = { "${it.service}:${it.artistId ?: it.user}:${it.id}" }) { post ->
            PostCard(
                post = post,
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
        leadingItemCount =
            leadingItemCount +
                if (
                    state.canLoadPrevious ||
                        state.isLoadingPrevious ||
                        !state.prependErrorMessage.isNullOrBlank()
                ) {
                  1
                } else {
                  0
                },
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
  LaunchedEffect(gridState, leadingItemCount) {
    snapshotFlow {
          val relativeIndex = (gridState.firstVisibleItemIndex - leadingItemCount).coerceAtLeast(0)
          PagingAnchor(
              itemKey =
                  state.posts.getOrNull(relativeIndex)?.let {
                    "${it.service}:${it.artistId ?: it.user}:${it.id}"
                  },
              index = relativeIndex,
              offset = gridState.firstVisibleItemScrollOffset,
          )
        }
        .distinctUntilChanged()
        .collect(actions.onViewportChanged)
  }
  LaunchedEffect(state.navigationEffect, leadingItemCount) {
    when (val effect = state.navigationEffect) {
      is PagingEffect.ScrollToTop -> gridState.scrollToItem(0)
      is PagingEffect.RestoreViewport -> {
        gridState.scrollToItem(
            (effect.anchor.index + leadingItemCount).coerceAtLeast(0),
            effect.anchor.offset,
        )
      }
      is PagingEffect.RebaseSelection,
      null -> Unit
    }
  }
  AutoLoadEffect(
      gridState,
      state.posts.size,
      hasMore = state.hasMore,
      isLoadingMore = state.isLoadingMore,
      onLoadMore = actions.onLoadMore,
  )
  PageJumpFabMenu(
      pageInfo = state.visiblePageInfo,
      loading = state.loading || state.isLoadingMore || state.isLoadingPrevious,
      onJumpToPage = actions.onJumpToPage,
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
  )
}

fun LazyGridScope.fullWidthItem(
    key: Any? = null,
    content: @Composable () -> Unit,
) {
  item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
