package ddd.kc.ui.pages.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.pages.tagposts.TagPostsScreen
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.filter_tags_hint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val TAG_RENDER_WINDOW_SIZE = 96

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

class TagsScreen(private val repeatSelectionToken: Int = 0) : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinInject<TagsScreenModel>()
    val state by screenModel.state.collectAsState()
    val platform = LocalActivePlatform.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastHandledRepeatToken by remember { mutableIntStateOf(repeatSelectionToken) }

    LaunchedEffect(platform) { screenModel.load(platform) }
    LaunchedEffect(repeatSelectionToken) {
      if (repeatSelectionToken == lastHandledRepeatToken) return@LaunchedEffect
      lastHandledRepeatToken = repeatSelectionToken
      if (listState.isAtTop) {
        screenModel.load(platform, forceRefresh = true)
      } else {
        scope.launch { listState.animateScrollToItem(0) }
      }
    }
    ErrorToastEffect(state.errorMessage)

    // Keep virtualization, but chunk by render windows instead of visual rows.
    // FlowRow can wrap naturally inside each window, while LazyColumn avoids composing all tags.
    val tagWindows =
        remember(state.filteredTags) { state.filteredTags.chunked(TAG_RENDER_WINDOW_SIZE) }

    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
      LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().padding(paddingValues),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        item(key = "filter") {
          OutlinedTextField(
              value = state.filter,
              onValueChange = { screenModel.onFilterChanged(it) },
              placeholder = { Text(stringResource(Res.string.filter_tags_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        }

        if (state.isLoading && state.filteredTags.isEmpty()) {
          item(key = "tags-skeleton") { ListLoadingSkeleton(itemHeightDp = 52) }
        } else {
          itemsIndexed(tagWindows, key = { idx, _ -> "tag-window-$idx" }) { _, window ->
            TagWindow(
                tags = window,
                onTagClick = { tag -> navigator.push(TagPostsScreen(platform, tag.tag)) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagWindow(tags: List<Tag>, onTagClick: (Tag) -> Unit) {
  FlowRow(
      modifier = Modifier.padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    tags.forEach { tag ->
      AssistChip(
          onClick = { onTagClick(tag) },
          label = { Text("${tag.tag} (${tag.count})") },
          modifier = Modifier.padding(vertical = 2.dp),
      )
    }
  }
}
