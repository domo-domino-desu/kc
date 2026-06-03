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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.LocalActivePlatform
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Platform
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.DateRangeW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowLeftW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.KeyboardArrowRightW400Outlined
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.PostCard
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.components.gridSkeletonItems
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.pages.post.PostRouteScreen
import ddd.kc.ui.pages.recent.PopularDateBoundary
import ddd.kc.ui.pages.recent.PopularDateShiftUnit
import ddd.kc.ui.pages.recent.PopularPeriod
import ddd.kc.ui.pages.recent.PopularPostsScreenModel
import ddd.kc.ui.pages.recent.PopularPostsState
import ddd.kc.ui.pages.tags.TagsScreen
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.date_end
import kc.shared.generated.resources.date_start
import kc.shared.generated.resources.period_day
import kc.shared.generated.resources.period_month
import kc.shared.generated.resources.period_week
import kc.shared.generated.resources.save
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val log = KcLog.withTag("WorksScreen")

class WorksScreen(private val repeatSelectionToken: Int = 0) : Screen {
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val platform = LocalActivePlatform.current
    var selectedIndex by remember { mutableIntStateOf(0) }
    var lastHandledRepeatToken by remember { mutableIntStateOf(repeatSelectionToken) }
    var popularRepeatToken by remember { mutableIntStateOf(0) }
    var searchRepeatToken by remember { mutableIntStateOf(0) }
    var tagsRepeatToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(repeatSelectionToken) {
      if (repeatSelectionToken == lastHandledRepeatToken) return@LaunchedEffect
      lastHandledRepeatToken = repeatSelectionToken
      when (selectedIndex) {
        0 -> popularRepeatToken += 1
        1 -> searchRepeatToken += 1
        2 -> tagsRepeatToken += 1
      }
    }

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
                checked = selectedIndex == index,
                label = label,
                onCheckedChange = { selectedIndex = index },
                weight = 1f,
            )
          }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
          when (selectedIndex) {
            0 -> PopularWorksContent(platform, popularRepeatToken)
            1 -> PostSearchContent(platform, searchRepeatToken)
            2 -> TagsScreen(tagsRepeatToken).Content()
          }
        }
      }
    }
  }
}

@Composable
private fun PopularWorksContent(platform: Platform, repeatSelectionToken: Int) {
  val navigator = LocalNavigator.currentOrThrow
  val popularModel = koinInject<PopularPostsScreenModel>()
  val state by popularModel.state.collectAsState()

  LaunchedEffect(platform) { popularModel.load(platform) }
  ErrorToastEffect(state.errorMessage)
  ErrorToastEffect(state.appendErrorMessage)

  val cellWidth = LocalAppSettings.current.cellMinWidthDp()
  val gridState = rememberLazyGridState()
  var lastHandledRepeatToken by remember { mutableIntStateOf(repeatSelectionToken) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(repeatSelectionToken) {
    if (repeatSelectionToken == lastHandledRepeatToken) return@LaunchedEffect
    lastHandledRepeatToken = repeatSelectionToken
    if (gridState.isAtTop) {
      popularModel.load(platform, forceRefresh = true)
    } else {
      scope.launch { gridState.animateScrollToItem(0) }
    }
  }
  AutoLoadEffect(
      gridState,
      state.posts.size,
      hasMore = state.hasMore,
      isLoadingMore = state.isLoadingMore,
  ) {
    popularModel.loadMore()
  }
  LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = cellWidth.dp),
      state = gridState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item(key = "popular-controls", span = { GridItemSpan(maxLineSpan) }) {
      PopularControls(
          state = state,
          onSelectPeriod = popularModel::selectPeriod,
          onSelectBoundaryDate = popularModel::selectBoundaryDate,
          onShiftManual = popularModel::shiftManual,
      )
    }
    if (state.isLoading && state.posts.isEmpty()) {
      items(List(12) { it }, key = { "popular-skeleton-$it" }) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(260.dp))
      }
    } else {
      items(state.posts, key = { it.id }) { post ->
        PostCard(
            post = post,
            platform = platform,
            onClick = {
              log.i {
                "打开Post -> 点击来源(source=popular,platform=${platform.name},${summarizePost(post)})"
              }
              navigator.push(
                  PostRouteScreen(
                      platform,
                      state.posts,
                      state.posts.indexOf(post),
                      source = "popular",
                  )
              )
            },
        )
      }
      loadingFooter(state.isLoadingMore, state.appendErrorMessage)
    }
  }
}

@Composable
private fun PostSearchContent(platform: Platform, repeatSelectionToken: Int) {
  val navigator = LocalNavigator.currentOrThrow
  val screenModel = koinInject<PostSearchScreenModel>()
  val state by screenModel.state.collectAsState()
  val cellWidth = LocalAppSettings.current.cellMinWidthDp()
  val gridState = rememberLazyGridState()
  var lastHandledRepeatToken by remember { mutableIntStateOf(repeatSelectionToken) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(platform) { screenModel.init(platform) }
  LaunchedEffect(repeatSelectionToken) {
    if (repeatSelectionToken == lastHandledRepeatToken) return@LaunchedEffect
    lastHandledRepeatToken = repeatSelectionToken
    if (gridState.isAtTop) {
      screenModel.refresh(forceRefreshDefault = true)
    } else {
      scope.launch { gridState.animateScrollToItem(0) }
    }
  }
  ErrorToastEffect(state.errorMessage)

  LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = cellWidth.dp),
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
          placeholder = { Text(stringResource(Res.string.search_works_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
      )
    }
    if (state.isLoading && state.posts.isEmpty()) {
      gridSkeletonItems()
    } else {
      items(state.posts, key = { it.id }) { post ->
        PostCard(
            post = post,
            platform = platform,
            onClick = {
              log.i {
                "打开Post -> 点击来源(source=post-search,platform=${platform.name},${summarizePost(post)})"
              }
              navigator.push(
                  PostRouteScreen(
                      platform,
                      state.posts,
                      state.posts.indexOf(post),
                      source = "post-search",
                  )
              )
            },
        )
      }
    }
  }
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
        FilledTonalIconButton(onClick = { onShiftManual(currentShiftUnit, -1) }) {
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
        FilledTonalIconButton(onClick = { onShiftManual(currentShiftUnit, 1) }) {
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

private const val MILLIS_PER_DAY = 86_400_000L

private fun isoDateToUtcMillis(date: String): Long? {
  val parts = date.split('-')
  if (parts.size != 3) return null
  val year = parts[0].toIntOrNull() ?: return null
  val month = parts[1].toIntOrNull() ?: return null
  val day = parts[2].toIntOrNull() ?: return null
  if (month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
  return daysFromCivil(year, month, day) * MILLIS_PER_DAY
}

private fun utcMillisToIsoDate(millis: Long): String {
  val days = floorDiv(millis, MILLIS_PER_DAY)
  val (year, month, day) = civilFromDays(days)
  return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun floorDiv(value: Long, divisor: Long): Long {
  var result = value / divisor
  if ((value xor divisor) < 0 && result * divisor != value) result -= 1
  return result
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
  val adjustedYear = year - if (month <= 2) 1 else 0
  val era = floorDiv(adjustedYear.toLong(), 400)
  val yearOfEra = adjustedYear - (era * 400).toInt()
  val adjustedMonth = month + if (month > 2) -3 else 9
  val dayOfYear = (153 * adjustedMonth + 2) / 5 + day - 1
  val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
  return era * 146097 + dayOfEra - 719468
}

private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
  val shiftedDays = daysSinceEpoch + 719468
  val era = floorDiv(shiftedDays, 146097)
  val dayOfEra = (shiftedDays - era * 146097).toInt()
  val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
  var year = yearOfEra + (era * 400).toInt()
  val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
  val monthPrime = (5 * dayOfYear + 2) / 153
  val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
  val month = monthPrime + if (monthPrime < 10) 3 else -9
  year += if (month <= 2) 1 else 0
  return Triple(year, month, day)
}

private fun daysInMonth(year: Int, month: Int): Int =
    when (month) {
      1,
      3,
      5,
      7,
      8,
      10,
      12 -> 31
      4,
      6,
      9,
      11 -> 30
      2 -> if (isLeapYear(year)) 29 else 28
      else -> 0
    }

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
