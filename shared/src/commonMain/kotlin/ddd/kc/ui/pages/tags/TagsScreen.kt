package ddd.kc.ui.pages.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import ddd.kc.LocalActivePlatform
import ddd.kc.data.model.Tag
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.TagW400Outlined
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.KcPullRefreshBox
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.pages.tagposts.TagPostsScreen
import ddd.kc.ui.state.ScrollPosition
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.filter_tags_hint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val TagListHorizontalPadding = 12.dp
private val TagHorizontalSpacing = 8.dp
private val AssistChipEstimatedHorizontalPadding = 32.dp
private const val TAG_SKELETON_ROW_COUNT = 24
private const val TAG_SKELETON_MIN_WIDTH_DP = 64
private const val TAG_SKELETON_MAX_WIDTH_DP = 188

object TagsTab : Tab {
  private fun readResolve(): Any = TagsTab

  override val options: TabOptions
    @Composable
    get() =
        TabOptions(
            index = 2u,
            title = "标签",
            icon = rememberVectorPainter(Icons.TagW400Outlined),
        )

  @Composable
  override fun Content() {
    Navigator(screen = TagsScreen())
  }
}

class TagsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    TagsContent()
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagsContent(
    onReselectHandlerChanged: (((() -> Unit)?) -> Unit) = {},
    initialScrollPosition: ScrollPosition = ScrollPosition(),
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> },
) {
  val navigator = LocalNavigator.currentOrThrow
  val screenModel = koinInject<TagsScreenModel>()
  val state by screenModel.state.collectAsState()
  val platform = LocalActivePlatform.current
  val listState =
      rememberLazyListState(
          initialFirstVisibleItemIndex = initialScrollPosition.index,
          initialFirstVisibleItemScrollOffset = initialScrollPosition.offset,
      )
  val scope = rememberCoroutineScope()
  val refresh = { screenModel.load(platform, forceRefresh = true) }
  val latestOnReselect by
      rememberUpdatedState<() -> Unit> {
        if (listState.isAtTop) {
          refresh()
        } else {
          scope.launch { listState.animateScrollToItem(0) }
        }
      }

  LaunchedEffect(platform) { screenModel.load(platform) }
  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        .distinctUntilChanged()
        .collect { (index, offset) -> onScrollPositionChanged(index, offset) }
  }
  DisposableEffect(onReselectHandlerChanged) {
    val handler = { latestOnReselect() }
    onReselectHandlerChanged(handler)
    onDispose { onReselectHandlerChanged(null) }
  }
  ErrorToastEffect(state.errorMessage)

  Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
    KcPullRefreshBox(
        enabled = !state.isLoading,
        refreshing = state.result.isRefreshing,
        onRefresh = refresh,
        modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val chipTextStyle = MaterialTheme.typography.labelLarge
        val filteredTags = state.filteredTags
        val rowWidthPx =
            with(density) {
              (maxWidth - TagListHorizontalPadding * 2).coerceAtLeast(0.dp).roundToPx()
            }
        val rowWidthDp = (maxWidth - TagListHorizontalPadding * 2).coerceAtLeast(0.dp).value.toInt()
        val horizontalSpacingPx = with(density) { TagHorizontalSpacing.roundToPx() }
        val chipHorizontalPaddingPx =
            with(density) { AssistChipEstimatedHorizontalPadding.roundToPx() }
        val chipWidthCache =
            remember(chipTextStyle, chipHorizontalPaddingPx) { mutableMapOf<String, Int>() }
        val layoutCacheKey =
            remember(
                filteredTags,
                rowWidthPx,
                horizontalSpacingPx,
                chipHorizontalPaddingPx,
                chipTextStyle,
            ) {
              TagsLayoutCacheKey(
                  tagsSize = filteredTags.size,
                  tagsSignature = tagListSignature(filteredTags),
                  rowWidthPx = rowWidthPx,
                  horizontalSpacingPx = horizontalSpacingPx,
                  chipHorizontalPaddingPx = chipHorizontalPaddingPx,
                  textStyleHash = chipTextStyle.hashCode(),
              )
            }
        val skeletonRows =
            remember(rowWidthPx, horizontalSpacingPx, density) {
              buildTagSkeletonRows(
                  maxRowWidthPx = rowWidthPx,
                  maxRowWidthDp = rowWidthDp,
                  horizontalSpacingPx = horizontalSpacingPx,
                  dpToPx = { dp -> with(density) { dp.dp.roundToPx() } },
              )
            }
        var tagRows by
            remember(layoutCacheKey) {
              mutableStateOf(screenModel.cachedTagRows(layoutCacheKey) ?: emptyList())
            }

        LaunchedEffect(
            layoutCacheKey,
        ) {
          screenModel.cachedTagRows(layoutCacheKey)?.let {
            tagRows = it
            return@LaunchedEffect
          }
          tagRows = emptyList()
          val measuredTags =
              measureTagWidths(
                  tags = filteredTags,
                  textMeasurer = textMeasurer,
                  textStyle = chipTextStyle,
                  chipHorizontalPaddingPx = chipHorizontalPaddingPx,
                  chipWidthCache = chipWidthCache,
              )
          tagRows =
              withContext(Dispatchers.Default) {
                buildTagRows(
                    measuredTags = measuredTags,
                    maxRowWidthPx = rowWidthPx,
                    horizontalSpacingPx = horizontalSpacingPx,
                )
              }
          screenModel.cacheTagRows(layoutCacheKey, tagRows)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = TagListHorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
          item(key = "filter") {
            if (state.isLoading && filteredTags.isEmpty()) {
              FilterSkeleton()
            } else {
              OutlinedTextField(
                  value = state.filter,
                  onValueChange = { screenModel.onFilterChanged(it) },
                  placeholder = { Text(stringResource(Res.string.filter_tags_hint)) },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          if (state.isLoading && filteredTags.isEmpty()) {
            tagSkeletonRows(keyPrefix = "tags-skeleton", skeletonRows = skeletonRows)
          } else if (filteredTags.isNotEmpty() && tagRows.isEmpty()) {
            tagSkeletonRows(keyPrefix = "tags-layout-skeleton", skeletonRows = skeletonRows)
          } else {
            itemsIndexed(tagRows, key = { index, _ -> "tag-row-$index" }) { _, row ->
              TagRow(
                  tags = row,
                  onTagClick = { tag -> navigator.push(TagPostsScreen(platform, tag.tag)) },
              )
            }
          }
        }
      }
    }
  }
}

private fun androidx.compose.foundation.lazy.LazyListScope.tagSkeletonRows(
    keyPrefix: String,
    skeletonRows: List<List<Int>>,
) {
  itemsIndexed(skeletonRows, key = { index, _ -> "$keyPrefix-$index" }) { _, widths ->
    TagSkeletonRow(widths)
  }
}

@Composable
private fun FilterSkeleton() {
  SkeletonBlock(
      modifier = Modifier.fillMaxWidth().height(56.dp),
      shape = RoundedCornerShape(4.dp),
  )
}

@Composable
private fun TagSkeletonRow(widths: List<Int>) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(TagHorizontalSpacing),
  ) {
    widths.forEach { width ->
      SkeletonBlock(
          modifier = Modifier.width(width.dp).height(32.dp),
          shape = RoundedCornerShape(8.dp),
      )
    }
  }
}

private fun buildTagSkeletonRows(
    maxRowWidthPx: Int,
    maxRowWidthDp: Int,
    horizontalSpacingPx: Int,
    dpToPx: (Int) -> Int,
): List<List<Int>> {
  if (maxRowWidthPx <= 0) return emptyList()

  return List(TAG_SKELETON_ROW_COUNT) { rowIndex ->
    val widths = mutableListOf<Int>()
    var usedWidthPx = 0
    var itemIndex = 0

    while (true) {
      val widthDp = skeletonWidthDp(rowIndex, itemIndex).coerceAtMost(maxRowWidthDp)
      val widthPx = dpToPx(widthDp)
      val nextWidthPx =
          if (widths.isEmpty()) widthPx else usedWidthPx + horizontalSpacingPx + widthPx
      if (widths.isNotEmpty() && nextWidthPx > maxRowWidthPx) break

      widths += widthDp
      usedWidthPx = nextWidthPx
      itemIndex += 1
    }

    widths
  }
}

private fun skeletonWidthDp(rowIndex: Int, itemIndex: Int): Int {
  val range = TAG_SKELETON_MAX_WIDTH_DP - TAG_SKELETON_MIN_WIDTH_DP
  val value = (rowIndex * 37 + itemIndex * 53 + rowIndex * itemIndex * 11) % (range + 1)
  return TAG_SKELETON_MIN_WIDTH_DP + value
}

private fun tagListSignature(tags: List<Tag>): Long =
    tags.fold(1125899906842597L) { acc, tag -> 31L * (31L * acc + tag.tag.hashCode()) + tag.count }

@Composable
private fun TagRow(tags: List<Tag>, onTagClick: (Tag) -> Unit) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    tags.forEach { tag ->
      AssistChip(
          onClick = { onTagClick(tag) },
          label = { Text(tag.label) },
      )
    }
  }
}

private suspend fun measureTagWidths(
    tags: List<Tag>,
    chipHorizontalPaddingPx: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    chipWidthCache: MutableMap<String, Int>,
): List<MeasuredTag> =
    tags.mapIndexed { index, tag ->
      if (index > 0 && index % 512 == 0) yield()
      val chipWidthPx =
          chipWidthCache.getOrPut(tag.label) {
            textMeasurer.measure(AnnotatedString(tag.label), style = textStyle).size.width +
                chipHorizontalPaddingPx
          }
      MeasuredTag(tag, chipWidthPx)
    }

private fun buildTagRows(
    measuredTags: List<MeasuredTag>,
    maxRowWidthPx: Int,
    horizontalSpacingPx: Int,
): List<List<Tag>> {
  if (measuredTags.isEmpty()) return emptyList()
  if (maxRowWidthPx <= 0) return measuredTags.map { listOf(it.tag) }

  val rows = mutableListOf<List<Tag>>()
  var currentRow = mutableListOf<Tag>()
  var currentWidthPx = 0

  measuredTags.forEach { measuredTag ->
    val tag = measuredTag.tag
    val chipWidthPx = measuredTag.widthPx
    val widthWithSpacing =
        if (currentRow.isEmpty()) chipWidthPx
        else currentWidthPx + horizontalSpacingPx + chipWidthPx

    if (currentRow.isNotEmpty() && widthWithSpacing > maxRowWidthPx) {
      rows += currentRow
      currentRow = mutableListOf()
      currentWidthPx = 0
    }

    currentRow += tag
    currentWidthPx =
        if (currentWidthPx == 0) chipWidthPx else currentWidthPx + horizontalSpacingPx + chipWidthPx
  }

  if (currentRow.isNotEmpty()) rows += currentRow
  return rows
}

private val Tag.label: String
  get() = "${tag} (${count})"

private data class MeasuredTag(
    val tag: Tag,
    val widthPx: Int,
)
