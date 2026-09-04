package ddd.kc.ui.pages.works

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.key
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.DateRangeW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowLeftW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowRightW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.SearchW400Outlined
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.app.i18n.localizedMessage
import ddd.kc.ui.app.navigation.AppScreen
import ddd.kc.ui.app.navigation.LocalNavigationWindowStore
import ddd.kc.ui.components.AiFilterField
import ddd.kc.ui.components.CollapsibleFilterPanel
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.PagedPostGrid
import ddd.kc.ui.components.PostGridPagingActions
import ddd.kc.ui.components.PostGridPagingState
import ddd.kc.ui.components.SearchResultStatus
import ddd.kc.ui.components.aiFilterLabel
import ddd.kc.ui.components.fullWidthItem
import ddd.kc.ui.components.gridSkeletonItems
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.paging.ScrollPosition
import ddd.kc.ui.components.shouldRefreshOnRepeatSelection
import ddd.kc.ui.pages.post.PostPagingContext
import ddd.kc.ui.pages.post.PostRouteScreen
import ddd.kc.ui.pages.recent.PopularDateBoundary
import ddd.kc.ui.pages.recent.PopularDateShiftUnit
import ddd.kc.ui.pages.recent.PopularPeriod
import ddd.kc.ui.pages.recent.PopularPostsScreenModel
import ddd.kc.ui.pages.recent.PopularPostsState
import ddd.kc.ui.pages.tags.TagsContent
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.date_end
import kc.shared.generated.resources.date_start
import kc.shared.generated.resources.period_day
import kc.shared.generated.resources.period_month
import kc.shared.generated.resources.period_week
import kc.shared.generated.resources.save
import kc.shared.generated.resources.search_action
import kc.shared.generated.resources.search_pending_prompt
import kc.shared.generated.resources.search_works_hint
import kc.shared.generated.resources.shift_next_day
import kc.shared.generated.resources.shift_next_month
import kc.shared.generated.resources.shift_next_week
import kc.shared.generated.resources.shift_prev_day
import kc.shared.generated.resources.shift_prev_month
import kc.shared.generated.resources.shift_prev_week
import kc.shared.generated.resources.works_tab_popular
import kc.shared.generated.resources.works_tab_search
import kc.shared.generated.resources.works_tab_tags
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val log = KcLog.withTag("WorksScreen")

@Serializable
class WorksScreen : AppScreen {
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val onReselectHandlerChanged = ddd.kc.ui.app.navigation.LocalRootTabReselectRegistration.current
    val worksModel = koinInject<WorksScreenModel>()
    val worksState by worksModel.state.collectAsState()

    val worksTabLabels =
        listOf(
            stringResource(Res.string.works_tab_popular),
            stringResource(Res.string.works_tab_search),
            stringResource(Res.string.works_tab_tags),
        )
    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          worksTabLabels.forEachIndexed { index, label ->
            toggleableItem(
                checked = worksState.selectedIndex == index,
                label = label,
                onCheckedChange = { worksModel.selectTab(index) },
                weight = 1f,
            )
          }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
          when (worksState.selectedIndex) {
            0 ->
                PopularWorksContent(
                    onReselectHandlerChanged = onReselectHandlerChanged,
                    initialScrollPosition = worksState.popularScroll,
                    onScrollPositionChanged = worksModel::onPopularScrollChanged,
                )
            1 ->
                PostSearchContent(
                    onReselectHandlerChanged = onReselectHandlerChanged,
                    initialScrollPosition = worksState.searchScroll,
                    onScrollPositionChanged = worksModel::onSearchScrollChanged,
                )
            2 ->
                TagsContent(
                    onReselectHandlerChanged = onReselectHandlerChanged,
                    initialScrollPosition = worksState.tagsScroll,
                    onScrollPositionChanged = worksModel::onTagsScrollChanged,
                )
          }
        }
      }
    }
  }
}

@Composable
private fun PopularWorksContent(
    onReselectHandlerChanged: (((() -> Unit)?) -> Unit),
    initialScrollPosition: ScrollPosition,
    onScrollPositionChanged: (Int, Int) -> Unit,
) {
  val navigator = LocalNavigator.currentOrThrow
  val navigationWindows = LocalNavigationWindowStore.current
  val popularModel = koinInject<PopularPostsScreenModel>()
  val state by popularModel.state.collectAsState()
  val refresh = { popularModel.load(forceRefresh = true) }

  LaunchedEffect(Unit) { popularModel.load() }
  ErrorToastEffect(state.error?.localizedMessage())
  ErrorToastEffect(state.appendError?.localizedMessage())

  val cellWidth = LocalAppSettings.current.cellMinWidthDp()
  val gridState =
      rememberLazyGridState(
          initialFirstVisibleItemIndex = initialScrollPosition.index,
          initialFirstVisibleItemScrollOffset = initialScrollPosition.offset,
      )
  val scope = rememberCoroutineScope()
  val latestOnReselect by
      rememberUpdatedState<() -> Unit> {
        if (shouldRefreshOnRepeatSelection(gridState.isAtTop, state.visiblePageInfo)) {
          refresh()
        } else {
          scope.launch { gridState.animateScrollToItem(0) }
        }
      }
  LaunchedEffect(gridState) {
    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
        .distinctUntilChanged()
        .collect { (index, offset) -> onScrollPositionChanged(index, offset) }
  }
  DisposableEffect(onReselectHandlerChanged) {
    val handler = { latestOnReselect() }
    onReselectHandlerChanged(handler)
    onDispose { onReselectHandlerChanged(null) }
  }
  PagedPostGrid(
      state =
          PostGridPagingState(
              posts = state.posts,
              visiblePageInfo = state.visiblePageInfo,
              loading = state.isLoading,
              refreshing = state.result.isRefreshing,
              isLoadingMore = state.isLoadingMore,
              isLoadingPrevious = state.isLoadingPrevious,
              hasMore = state.hasMore,
              canLoadPrevious = state.canAutoLoadPrevious,
              prependErrorMessage = state.prependError?.localizedMessage(),
              appendErrorMessage = state.appendError?.localizedMessage(),
              navigationEffect = state.paging.navigationEffect,
          ),
      actions =
          PostGridPagingActions(
              onRefresh = refresh,
              onLoadMore = popularModel::loadMore,
              onLoadPrevious = popularModel::loadPrevious,
              onJumpToPage = popularModel::jumpToPage,
              onViewportChanged = popularModel::onViewportChanged,
              onPostClick = { post ->
                log.i { "打开Post -> 点击来源(source=popular,${summarizePost(post)})" }
                navigator.push(
                    PostRouteScreen(
                        windowId = navigationWindows.putPosts(state.posts),
                        resourceKey = post.key,
                        startIndex = state.posts.indexOf(post),
                        source = "popular",
                        initialOffset = state.startOffset,
                        initialHasMore = state.hasMore,
                        pagingContext =
                            PostPagingContext.Popular(
                                date = state.date,
                                period = state.period.apiValue,
                                aiFilter = state.aiFilter,
                            ),
                    )
                )
              },
          ),
      gridState = gridState,
      minCardWidth = cellWidth.dp,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      leadingItemCount = 1,
      leadingContent = {
        fullWidthItem(key = "popular-controls") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollapsibleFilterPanel(chips = listOf(aiFilterLabel(state.aiFilter))) {
              AiFilterField(state.aiFilter, popularModel::onAiFilterChanged)
            }
            PopularControls(
                state = state,
                onSelectPeriod = popularModel::selectPeriod,
                onSelectBoundaryDate = popularModel::selectBoundaryDate,
                onShiftManual = popularModel::shiftManual,
            )
          }
        }
      },
  )
}

@Composable
private fun PostSearchContent(
    onReselectHandlerChanged: (((() -> Unit)?) -> Unit),
    initialScrollPosition: ScrollPosition,
    onScrollPositionChanged: (Int, Int) -> Unit,
) {
  val navigator = LocalNavigator.currentOrThrow
  val navigationWindows = LocalNavigationWindowStore.current
  val screenModel = koinInject<PostSearchScreenModel>()
  val state by screenModel.state.collectAsState()
  val cellWidth = LocalAppSettings.current.cellMinWidthDp()
  val refresh = { screenModel.refresh(forceRefreshDefault = true) }
  val gridState =
      rememberLazyGridState(
          initialFirstVisibleItemIndex = initialScrollPosition.index,
          initialFirstVisibleItemScrollOffset = initialScrollPosition.offset,
      )
  val scope = rememberCoroutineScope()
  val latestOnReselect by
      rememberUpdatedState<() -> Unit> {
        if (shouldRefreshOnRepeatSelection(gridState.isAtTop, state.visiblePageInfo)) {
          refresh()
        } else {
          scope.launch { gridState.animateScrollToItem(0) }
        }
      }

  LaunchedEffect(Unit) { screenModel.init() }
  LaunchedEffect(gridState) {
    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
        .distinctUntilChanged()
        .collect { (index, offset) -> onScrollPositionChanged(index, offset) }
  }
  DisposableEffect(onReselectHandlerChanged) {
    val handler = { latestOnReselect() }
    onReselectHandlerChanged(handler)
    onDispose { onReselectHandlerChanged(null) }
  }
  ErrorToastEffect(state.error?.localizedMessage())
  ErrorToastEffect(state.appendError?.localizedMessage())
  ErrorToastEffect(state.prependError?.localizedMessage())
  val visiblePosts = if (state.hasPendingQuery) emptyList() else state.posts
  PagedPostGrid(
      state =
          PostGridPagingState(
              posts = visiblePosts,
              visiblePageInfo = state.visiblePageInfo.takeUnless { state.hasPendingQuery },
              loading = state.isLoading && !state.hasPendingQuery,
              refreshing = state.result.isRefreshing && !state.hasPendingQuery,
              isLoadingMore = state.isLoadingMore && !state.hasPendingQuery,
              isLoadingPrevious = state.isLoadingPrevious && !state.hasPendingQuery,
              hasMore = state.hasMore && state.posts.isNotEmpty() && !state.hasPendingQuery,
              canLoadPrevious = state.canAutoLoadPrevious && !state.hasPendingQuery,
              prependErrorMessage = state.prependError?.localizedMessage(),
              appendErrorMessage = state.appendError?.localizedMessage(),
              navigationEffect = state.paging.navigationEffect,
          ),
      actions =
          PostGridPagingActions(
              onRefresh = refresh,
              onLoadMore = screenModel::loadMore,
              onLoadPrevious = screenModel::loadPrevious,
              onJumpToPage = screenModel::jumpToPage,
              onViewportChanged = screenModel::onViewportChanged,
              onPostClick = { post ->
                log.i { "打开Post -> 点击来源(source=post-search,${summarizePost(post)})" }
                navigator.push(
                    PostRouteScreen(
                        windowId = navigationWindows.putPosts(state.posts),
                        resourceKey = post.key,
                        startIndex = state.posts.indexOf(post),
                        source = "post-search",
                        initialOffset = state.startOffset,
                        initialHasMore = state.hasMore,
                        pagingContext =
                            PostPagingContext.Search(
                                query = state.query,
                                defaultPopularDate = state.defaultPopularDate,
                                aiFilter = state.aiFilter,
                            ),
                    )
                )
              },
          ),
      gridState = gridState,
      minCardWidth = cellWidth.dp,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      leadingItemCount = if (state.hasPendingQuery) 3 else 2,
      leadingContent = {
        fullWidthItem(key = "search_bar") {
          OutlinedTextField(
              value = state.draftQuery,
              onValueChange = screenModel::onQueryChanged,
              placeholder = { Text(stringResource(Res.string.search_works_hint)) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = { screenModel.submitSearch() }),
              trailingIcon = {
                IconButton(onClick = screenModel::submitSearch, enabled = !state.isLoading) {
                  Icon(
                      imageVector = Icons.SearchW400Outlined,
                      contentDescription = stringResource(Res.string.search_action),
                  )
                }
              },
              modifier = Modifier.fillMaxWidth(),
          )
        }
        fullWidthItem(key = "search_filter") {
          CollapsibleFilterPanel(chips = listOf(aiFilterLabel(state.aiFilter))) {
            AiFilterField(state.aiFilter, screenModel::onAiFilterChanged)
          }
        }
        if (state.hasPendingQuery) {
          fullWidthItem(key = "search_prompt") {
            Text(
                text = stringResource(Res.string.search_pending_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
          }
        }
      },
      initialLoadingContent = {
        if (state.query.isBlank()) {
          gridSkeletonItems()
        } else {
          fullWidthItem(key = "search_loading") {
            SearchResultStatus(loading = true, errorMessage = null, onRetry = refresh)
          }
        }
      },
      emptyContent = {
        if (!state.hasPendingQuery && state.query.isNotBlank()) {
          fullWidthItem(key = if (state.error == null) "search_empty" else "search_error") {
            SearchResultStatus(
                loading = false,
                errorMessage = state.error?.localizedMessage(),
                onRetry = refresh,
            )
          }
        }
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PopularControls(
    state: PopularPostsState,
    onSelectPeriod: (PopularPeriod) -> Unit,
    onSelectBoundaryDate: (PopularDateBoundary, String) -> Unit,
    onShiftManual: (PopularDateShiftUnit, Int) -> Unit,
) {
  var datePickerTarget by remember { mutableStateOf<PopularDateBoundary?>(null) }
  val info = state.info
  val startDate = info?.minDate?.take(10).orEmpty()
  val endDate = info?.maxDate?.take(10).orEmpty()
  Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PopularDateField(
            value = startDate,
            label = stringResource(Res.string.date_start),
            onClick = { datePickerTarget = PopularDateBoundary.START },
            modifier = Modifier.weight(1f),
        )
        PopularDateField(
            value = endDate,
            label = stringResource(Res.string.date_end),
            onClick = { datePickerTarget = PopularDateBoundary.END },
            modifier = Modifier.weight(1f),
        )
      }
      val dayLabel = stringResource(Res.string.period_day)
      val weekLabel = stringResource(Res.string.period_week)
      val monthLabel = stringResource(Res.string.period_month)
      val currentShiftUnit =
          when (state.period) {
            PopularPeriod.DAY -> PopularDateShiftUnit.DAY
            PopularPeriod.WEEK -> PopularDateShiftUnit.WEEK
            PopularPeriod.MONTH -> PopularDateShiftUnit.MONTH
          }
      val prevDescription =
          when (state.period) {
            PopularPeriod.DAY -> stringResource(Res.string.shift_prev_day)
            PopularPeriod.WEEK -> stringResource(Res.string.shift_prev_week)
            PopularPeriod.MONTH -> stringResource(Res.string.shift_prev_month)
          }
      val nextDescription =
          when (state.period) {
            PopularPeriod.DAY -> stringResource(Res.string.shift_next_day)
            PopularPeriod.WEEK -> stringResource(Res.string.shift_next_week)
            PopularPeriod.MONTH -> stringResource(Res.string.shift_next_month)
          }
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalIconButton(
            onClick = { onShiftManual(currentShiftUnit, -1) },
            enabled = state.canShiftPrevious,
        ) {
          Icon(
              imageVector = Icons.KeyboardArrowLeftW400Outlined,
              contentDescription = prevDescription,
          )
        }
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            horizontalArrangement = ButtonGroupDefaults.HorizontalArrangement,
            modifier = Modifier.weight(1f),
        ) {
          PopularPeriod.entries.forEachIndexed { index, period ->
            val periodLabel =
                when (period) {
                  PopularPeriod.DAY -> dayLabel
                  PopularPeriod.WEEK -> weekLabel
                  PopularPeriod.MONTH -> monthLabel
                }
            toggleableItem(
                checked = state.period == period,
                label = periodLabel,
                onCheckedChange = { onSelectPeriod(period) },
                weight = 1f,
            )
          }
        }
        FilledTonalIconButton(
            onClick = { onShiftManual(currentShiftUnit, 1) },
            enabled = state.canShiftNext,
        ) {
          Icon(
              imageVector = Icons.KeyboardArrowRightW400Outlined,
              contentDescription = nextDescription,
          )
        }
      }
    }
  }
  datePickerTarget?.let { target ->
    val initialDate =
        when (target) {
          PopularDateBoundary.START -> startDate
          PopularDateBoundary.END -> endDate
        }.ifBlank { state.date?.take(10).orEmpty() }
    val datePickerState =
        rememberDatePickerState(initialSelectedDateMillis = isoDateToUtcMillis(initialDate))
    DatePickerDialog(
        onDismissRequest = { datePickerTarget = null },
        confirmButton = {
          TextButton(
              onClick = {
                val selectedDate = datePickerState.selectedDateMillis?.let(::utcMillisToIsoDate)
                if (selectedDate != null) {
                  onSelectBoundaryDate(target, selectedDate)
                }
                datePickerTarget = null
              }
          ) {
            Text(stringResource(Res.string.save))
          }
        },
        dismissButton = {
          TextButton(onClick = { datePickerTarget = null }) {
            Text(stringResource(Res.string.cancel))
          }
        },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@Composable
private fun PopularDateField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
          Icon(
              imageVector = Icons.DateRangeW400Outlined,
              contentDescription = label,
          )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick))
  }
}

private fun isoDateToUtcMillis(date: String): Long? {
  return runCatching {
        LocalDate.parse(date.take(10)).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
      }
      .getOrNull()
}

private fun utcMillisToIsoDate(millis: Long): String {
  return kotlin.time.Instant.fromEpochMilliseconds(millis)
      .toLocalDateTime(TimeZone.UTC)
      .date
      .toString()
}
