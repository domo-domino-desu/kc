package ddd.kc.ui.pages.creators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.LocalActivePlatform
import ddd.kc.data.model.services
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ExpandMoreW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.SortW400Outlined
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.AutoLoadPreviousEffect
import ddd.kc.ui.components.CreatorSearchCard
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.KcPullRefreshBox
import ddd.kc.ui.components.PageJumpFabMenu
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.components.shouldRefreshOnRepeatSelection
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.filter_all
import kc.shared.generated.resources.filter_service
import kc.shared.generated.resources.filter_sort
import kc.shared.generated.resources.search_creators_hint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class CreatorsScreen(
    private val onReselectHandlerChanged: (((() -> Unit)?) -> Unit) = {},
) : Screen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinInject<CreatorSearchScreenModel>()
    val state by screenModel.state.collectAsState()
    val platform = LocalActivePlatform.current
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val refresh = { screenModel.refresh() }
    val latestOnReselect by
        rememberUpdatedState<() -> Unit> {
          if (shouldRefreshOnRepeatSelection(gridState.isAtTop, state.visiblePageInfo)) {
            refresh()
          } else {
            scope.launch { gridState.animateScrollToItem(0) }
          }
        }

    LaunchedEffect(platform) { screenModel.init(platform) }
    LaunchedEffect(gridState) {
      snapshotFlow { gridState.firstVisibleItemIndex }
          .distinctUntilChanged()
          .collect { index -> screenModel.onVisibleCreatorIndex(index - 2) }
    }
    DisposableEffect(onReselectHandlerChanged) {
      val handler = { latestOnReselect() }
      onReselectHandlerChanged(handler)
      onDispose { onReselectHandlerChanged(null) }
    }
    ErrorToastEffect(state.errorMessage)

    var serviceDropdownExpanded by remember { mutableStateOf(false) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
      AutoLoadEffect(
          gridState,
          state.creators.size,
          hasMore = state.hasMore,
          isLoadingMore = state.isLoadingMore,
          onLoadMore = screenModel::loadMore,
      )
      AutoLoadPreviousEffect(
          gridState,
          state.creators.size,
          hasPrevious = state.canAutoLoadPrevious,
          isLoadingPrevious = state.isLoadingPrevious,
          onLoadPrevious = screenModel::loadPrevious,
      )
      Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        KcPullRefreshBox(
            enabled = !state.isLoading,
            refreshing = state.result.isRefreshing,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
          LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 320.dp),
              state = gridState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item(key = "search_bar", span = { GridItemSpan(maxLineSpan) }) {
              OutlinedTextField(
                  value = state.query,
                  onValueChange = screenModel::onQueryChanged,
                  placeholder = { Text(stringResource(Res.string.search_creators_hint)) },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
              )
            }

            item(key = "artist_filters", span = { GridItemSpan(maxLineSpan) }) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                CreatorFilterChipDropdown(
                    label = stringResource(Res.string.filter_service),
                    selectedLabel =
                        state.selectedService?.replaceFirstChar { it.uppercase() }
                            ?: stringResource(Res.string.filter_all),
                    expanded = serviceDropdownExpanded,
                    onExpandedChange = { serviceDropdownExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                  DropdownMenuItem(
                      text = { Text(stringResource(Res.string.filter_all)) },
                      onClick = {
                        screenModel.onServiceChanged(null)
                        serviceDropdownExpanded = false
                      },
                  )
                  platform.services().forEach { service ->
                    DropdownMenuItem(
                        text = { Text(service.replaceFirstChar { it.uppercase() }) },
                        onClick = {
                          screenModel.onServiceChanged(service)
                          serviceDropdownExpanded = false
                        },
                    )
                  }
                }

                Spacer(Modifier.width(8.dp))

                CreatorFilterChipDropdown(
                    label = stringResource(Res.string.filter_sort),
                    selectedLabel = creatorSortLabel(state.sortBy),
                    expanded = sortDropdownExpanded,
                    onExpandedChange = { sortDropdownExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                  CreatorSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(creatorSortLabel(sort)) },
                        onClick = {
                          screenModel.onSortChanged(sort)
                          sortDropdownExpanded = false
                        },
                    )
                  }
                }

                Spacer(Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = {
                      screenModel.onSortOrderChanged(
                          if (state.sortOrder == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC
                      )
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                ) {
                  Icon(
                      imageVector = Icons.SortW400Outlined,
                      contentDescription = sortOrderLabel(state.sortOrder),
                      modifier =
                          Modifier.graphicsLayer {
                            scaleY = if (state.sortOrder == SortOrder.DESC) 1f else -1f
                          },
                  )
                }
              }
            }

            if (state.isLoading && state.creators.isEmpty()) {
              items(List(64) { it }, key = { "artists-skeleton-$it" }) {
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    shape = RoundedCornerShape(8.dp),
                )
              }
            } else {
              items(state.creators, key = { "${it.service}:${it.id}" }) { creator ->
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
              loadingFooter(state.isLoadingMore, state.appendErrorMessage)
            }
          }
        }
        PageJumpFabMenu(
            pageInfo = state.visiblePageInfo,
            loading = state.isLoading || state.isLoadingMore || state.isLoadingPrevious,
            onJumpToPage = { page ->
              screenModel.jumpToPage(page)
              scope.launch { gridState.scrollToItem(0) }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
      }
    }
  }
}

@Composable
private fun CreatorFilterChipDropdown(
    label: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Box(modifier = Modifier.weight(1f)) {
      FilterChip(
          selected = true,
          onClick = { onExpandedChange(true) },
          label = {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          },
          trailingIcon = {
            Icon(
                imageVector = Icons.ExpandMoreW400Outlined,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
          },
          modifier = Modifier.fillMaxWidth().height(40.dp),
      )

      DropdownMenu(
          expanded = expanded,
          onDismissRequest = { onExpandedChange(false) },
          content = menuContent,
      )
    }
  }
}
