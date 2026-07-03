package ddd.kc.ui.pages.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import ddd.kc.data.model.DM
import ddd.kc.data.translation.TranslationBlock
import ddd.kc.data.translation.TranslationBlockResult
import ddd.kc.data.translation.TranslationEngine
import ddd.kc.ui.app.LocalActivePlatform
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.AutoLoadPreviousEffect
import ddd.kc.ui.components.DmCard
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.KcPullRefreshBox
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.PageJumpFabMenu
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.components.shouldRefreshOnRepeatSelection
import ddd.kc.ui.pages.recent.RecentDMsScreenModel
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.TranslationBlockState
import ddd.kc.ui.state.TranslationStatus
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.search_dms_hint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class DmScreen(
    private val onReselectHandlerChanged: (((() -> Unit)?) -> Unit) = {},
) : Screen {
  @Composable
  override fun Content() {
    val platform = LocalActivePlatform.current
    val searchModel = koinInject<DmSearchScreenModel>()
    val recentModel = koinInject<RecentDMsScreenModel>()
    val translationService = koinInject<TranslationEngine>()
    val searchState by searchModel.state.collectAsState()
    val recentState by recentModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dmTranslations = remember(platform) { mutableStateMapOf<String, ContentTranslationState>() }
    val refresh = {
      if (searchState.query.isBlank()) {
        recentModel.load(platform, forceRefresh = true)
      } else {
        searchModel.refresh()
      }
    }
    val latestOnReselect by
        rememberUpdatedState<() -> Unit> {
          val visiblePageInfo =
              if (searchState.query.isBlank()) {
                recentState.visiblePageInfo
              } else {
                searchState.visiblePageInfo
              }
          if (shouldRefreshOnRepeatSelection(listState.isAtTop, visiblePageInfo)) {
            refresh()
          } else {
            scope.launch { listState.animateScrollToItem(0) }
          }
        }

    LaunchedEffect(platform) {
      searchModel.init(platform)
      recentModel.load(platform)
    }
    LaunchedEffect(listState, searchState.query.isBlank()) {
      snapshotFlow { listState.firstVisibleItemIndex }
          .distinctUntilChanged()
          .collect { index ->
            if (searchState.query.isBlank()) {
              recentModel.onVisibleItemIndex(index - 1)
            } else {
              searchModel.onVisibleItemIndex(index - 1)
            }
          }
    }
    DisposableEffect(onReselectHandlerChanged) {
      val handler = { latestOnReselect() }
      onReselectHandlerChanged(handler)
      onDispose { onReselectHandlerChanged(null) }
    }
    ErrorToastEffect(searchState.errorMessage)
    ErrorToastEffect(searchState.appendErrorMessage)
    ErrorToastEffect(searchState.prependErrorMessage)

    val translateDm: (DM) -> Unit = translateDm@{ dm ->
      if (!translationService.isEnabled()) return@translateDm
      val content = dm.content?.takeIf { it.isNotBlank() } ?: return@translateDm
      val key = dm.translationKey()
      val existing = dmTranslations[key]
      if (existing?.showTranslation == true) {
        dmTranslations[key] = existing.copy(showTranslation = false)
        return@translateDm
      }
      if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
        dmTranslations[key] = existing.copy(showTranslation = true)
        return@translateDm
      }
      scope.launch {
        val blocks = buildDmTranslationBlocks(content)
        if (blocks.isEmpty()) return@launch
        dmTranslations[key] =
            ContentTranslationState(
                blocks =
                    blocks.map {
                      TranslationBlockState(
                          originalHtml = it.originalHtml,
                          status = TranslationStatus.PENDING,
                      )
                    },
                isTranslating = true,
                showTranslation = true,
            )
        runCatching {
              translationService.translateBlocks(blocks) { index, result ->
                val current = dmTranslations[key] ?: return@translateBlocks
                if (index !in current.blocks.indices) return@translateBlocks
                val updatedBlocks = current.blocks.toMutableList()
                updatedBlocks[index] =
                    when (result) {
                      is TranslationBlockResult.Success ->
                          updatedBlocks[index].copy(
                              translated = result.translatedText,
                              status = TranslationStatus.SUCCESS,
                          )
                      TranslationBlockResult.EmptyResult ->
                          updatedBlocks[index].copy(status = TranslationStatus.EMPTY)
                      is TranslationBlockResult.Failure ->
                          updatedBlocks[index].copy(status = TranslationStatus.FAILURE)
                    }
                dmTranslations[key] = current.copy(blocks = updatedBlocks, showTranslation = true)
              }
            }
            .onFailure {
              val failed = dmTranslations[key] ?: ContentTranslationState()
              dmTranslations[key] =
                  failed.copy(
                      blocks =
                          failed.blocks.map { block ->
                            if (block.status == TranslationStatus.PENDING)
                                block.copy(status = TranslationStatus.FAILURE)
                            else block
                          },
                      isTranslating = false,
                      showTranslation = true,
                  )
            }
        dmTranslations[key]?.let {
          dmTranslations[key] = it.copy(isTranslating = false, showTranslation = true)
        }
      }
    }

    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
      val isSearchMode = searchState.query.isNotBlank()
      Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        KcPullRefreshBox(
            enabled = if (isSearchMode) !searchState.isLoading else !recentState.loading,
            refreshing =
                if (isSearchMode) searchState.result.isRefreshing else recentState.refreshing,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
          LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item(key = "search_bar") {
              OutlinedTextField(
                  value = searchState.query,
                  onValueChange = searchModel::onQueryChanged,
                  placeholder = { Text(stringResource(Res.string.search_dms_hint)) },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
              )
            }

            if (searchState.query.isBlank()) {
              if (recentState.loading && recentState.items.isEmpty()) {
                item(key = "recent-dms-skeleton") { ListLoadingSkeleton(itemHeightDp = 96) }
              } else {
                items(recentState.items, key = { it.hash ?: it.content.orEmpty() }) { dm ->
                  DmCard(
                      dm = dm,
                      platform = platform,
                      translationState = dmTranslations[dm.translationKey()],
                      onTranslate = { translateDm(dm) },
                  )
                }
                loadingFooter(recentState.isLoadingMore, recentState.appendErrorMessage)
              }
            } else if (searchState.isLoading && searchState.dms.isEmpty()) {
              item(key = "search-dms-skeleton") { ListLoadingSkeleton(itemHeightDp = 96) }
            } else {
              items(searchState.dms, key = { it.hash ?: it.content.orEmpty() }) { dm ->
                DmCard(
                    dm = dm,
                    platform = platform,
                    translationState = dmTranslations[dm.translationKey()],
                    onTranslate = { translateDm(dm) },
                )
              }
              loadingFooter(searchState.isLoadingMore, searchState.appendErrorMessage)
            }
          }
        }
        val pageInfo =
            if (isSearchMode) searchState.visiblePageInfo else recentState.visiblePageInfo
        PageJumpFabMenu(
            pageInfo = pageInfo,
            loading =
                if (isSearchMode) {
                  searchState.isLoading ||
                      searchState.isLoadingMore ||
                      searchState.isLoadingPrevious
                } else {
                  recentState.loading || recentState.isLoadingMore || recentState.isLoadingPrevious
                },
            onJumpToPage = { page ->
              if (isSearchMode) searchModel.jumpToPage(page) else recentModel.jumpToPage(page)
              scope.launch { listState.scrollToItem(0) }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
      }
    }

    ErrorToastEffect(recentState.errorMessage)
    ErrorToastEffect(recentState.appendErrorMessage)
    if (searchState.query.isBlank()) {
      AutoLoadEffect(
          listState = listState,
          totalItems = recentState.items.size,
          hasMore = recentState.hasMore,
          isLoadingMore = recentState.isLoadingMore,
          onLoadMore = recentModel::loadMore,
      )
      AutoLoadPreviousEffect(
          listState = listState,
          totalItems = recentState.items.size,
          hasPrevious = recentState.canAutoLoadPrevious,
          isLoadingPrevious = recentState.isLoadingPrevious,
          onLoadPrevious = recentModel::loadPrevious,
      )
    } else {
      AutoLoadEffect(
          listState = listState,
          totalItems = searchState.dms.size,
          hasMore = searchState.hasMore,
          isLoadingMore = searchState.isLoadingMore,
          onLoadMore = searchModel::loadMore,
      )
      AutoLoadPreviousEffect(
          listState = listState,
          totalItems = searchState.dms.size,
          hasPrevious = searchState.canAutoLoadPrevious,
          isLoadingPrevious = searchState.isLoadingPrevious,
          onLoadPrevious = searchModel::loadPrevious,
      )
    }
  }
}

private fun DM.translationKey(): String =
    hash
        ?: id
        ?: "${service.orEmpty()}:${user.orEmpty()}:${added.orEmpty()}:${content.orEmpty().hashCode()}"

private fun buildDmTranslationBlocks(content: String): List<TranslationBlock> =
    content.split(Regex("""(?:\r?\n[ \t]*){2,}""")).mapNotNull { block ->
      val text = block.trim()
      if (text.isBlank()) null else TranslationBlock(originalHtml = text, sourceText = text)
    }
