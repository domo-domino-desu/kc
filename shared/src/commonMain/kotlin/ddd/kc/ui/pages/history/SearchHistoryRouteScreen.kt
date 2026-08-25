package ddd.kc.ui.pages.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.local.ActivityHistoryRepository
import ddd.kc.data.model.SearchHistoryRecord
import ddd.kc.data.model.SearchKind
import ddd.kc.ui.app.navigation.AppScreen
import ddd.kc.ui.app.navigation.LocalKcTabNavigationController
import ddd.kc.ui.app.navigation.MainTab
import ddd.kc.ui.app.navigation.nextRouteInstanceKey
import ddd.kc.ui.components.BackAppBar
import ddd.kc.ui.pages.dm.DmSearchScreenModel
import ddd.kc.ui.pages.works.PostSearchScreenModel
import ddd.kc.ui.pages.works.WorksScreenModel
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.empty_search_history
import kc.shared.generated.resources.loading_search_history
import kc.shared.generated.resources.search_history
import kc.shared.generated.resources.search_history_dms
import kc.shared.generated.resources.search_history_posts
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Serializable
class SearchHistoryRouteScreen(
    private val routeKey: String = nextRouteInstanceKey("search-history"),
) : AppScreen {
  override val key: String = routeKey

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val tabNavigation = LocalKcTabNavigationController.current
    val repository = koinInject<ActivityHistoryRepository>()
    val postSearchModel = koinInject<PostSearchScreenModel>()
    val dmSearchModel = koinInject<DmSearchScreenModel>()
    val worksModel = koinInject<WorksScreenModel>()
    var selectedKind by remember { mutableStateOf(SearchKind.POSTS) }
    var loading by remember { mutableStateOf(true) }
    var records by remember { mutableStateOf<List<SearchHistoryRecord>>(emptyList()) }

    LaunchedEffect(selectedKind) {
      loading = true
      records = repository.loadSearchHistory(selectedKind)
      loading = false
    }

    Scaffold(topBar = { BackAppBar(stringResource(Res.string.search_history)) }) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        val labels =
            listOf(
                stringResource(Res.string.search_history_posts),
                stringResource(Res.string.search_history_dms),
            )
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          SearchKind.entries.forEachIndexed { index, kind ->
            toggleableItem(
                checked = selectedKind == kind,
                label = labels[index],
                onCheckedChange = { selectedKind = kind },
                weight = 1f,
            )
          }
        }
        when {
          loading -> HistoryMessage(stringResource(Res.string.loading_search_history))
          records.isEmpty() -> HistoryMessage(stringResource(Res.string.empty_search_history))
          else ->
              LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(records, key = { "${it.kind}:${it.query.lowercase()}" }) { record ->
                  Surface(
                      shape = RoundedCornerShape(14.dp),
                      border =
                          BorderStroke(
                              1.dp,
                              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
                          ),
                      modifier =
                          Modifier.fillMaxWidth().clickable {
                            when (record.kind) {
                              SearchKind.POSTS -> {
                                postSearchModel.onQueryChanged(record.query)
                                postSearchModel.submitSearch()
                                worksModel.selectTab(1)
                                navigator.pop()
                                tabNavigation.select(MainTab.Works)
                              }
                              SearchKind.DMS -> {
                                dmSearchModel.onQueryChanged(record.query)
                                dmSearchModel.submitSearch()
                                navigator.pop()
                                tabNavigation.select(MainTab.Dm)
                              }
                            }
                          },
                  ) {
                    Text(
                        text = record.query,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
private fun HistoryMessage(text: String) {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
  )
}
