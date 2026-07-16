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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DM
import ddd.kc.data.model.DmKey
import ddd.kc.data.model.key
import ddd.kc.data.remote.translation.TranslationBlock
import ddd.kc.data.remote.translation.TranslationBlockResult
import ddd.kc.data.remote.translation.TranslationEngine
import ddd.kc.ui.app.i18n.localizedMessage
import ddd.kc.ui.app.navigation.AppScreen
import ddd.kc.ui.app.navigation.LocalNavigationWindowStore
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.DmCard
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.PageJumpFabMenu
import ddd.kc.ui.components.PagedPullRefreshBox
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.components.paging.ContentTranslationState
import ddd.kc.ui.components.paging.PagingAnchor
import ddd.kc.ui.components.paging.PagingEffect
import ddd.kc.ui.components.paging.TranslationBlockState
import ddd.kc.ui.components.paging.TranslationStatus
import ddd.kc.ui.components.previousPageHeader
import ddd.kc.ui.components.shouldRefreshOnRepeatSelection
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.recent.RecentDMsScreenModel
import ddd.kc.utils.coroutines.resultOfSuspend
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.search_dms_hint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Serializable
class DmScreen : AppScreen {
  @Composable
  override fun Content() {
    val onReselectHandlerChanged = ddd.kc.ui.app.navigation.LocalRootTabReselectRegistration.current
    val searchModel = koinInject<DmSearchScreenModel>()
    val recentModel = koinInject<RecentDMsScreenModel>()
    val translationService = koinInject<TranslationEngine>()
    val navigator = LocalNavigator.currentOrThrow
    val navigationWindows = LocalNavigationWindowStore.current
    val searchState by searchModel.state.collectAsState()
    val recentState by recentModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dmTranslations = remember { mutableStateMapOf<DmKey, ContentTranslationState>() }
    val openCreator: (DM) -> Unit = openCreator@{ dm ->
      val service = dm.service?.takeIf { it.isNotBlank() } ?: return@openCreator
      val userId = dm.user?.takeIf { it.isNotBlank() } ?: return@openCreator
      val creator =
          dm.artist?.takeIf { it.service == service && it.id == userId }
              ?: Creator(
                  id = userId,
                  name = dm.artist?.name?.takeIf { it.isNotBlank() } ?: userId,
                  service = service,
                  publicId = userId,
              )
      navigator.push(
          CreatorRouteScreen(
              navigationWindows.putCreators(listOf(creator)),
              creator.key,
              startIndex = 0,
          )
      )
    }
    val refresh = {
      if (searchState.query.isBlank()) {
        recentModel.load(forceRefresh = true)
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

    LaunchedEffect(Unit) {
      searchModel.init()
      recentModel.load()
    }
    LaunchedEffect(listState, searchState.query.isBlank(), searchState.dms, recentState.items) {
      snapshotFlow {
            val index = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
            val itemKey =
                if (searchState.query.isBlank()) {
                  recentState.items.getOrNull(index)?.let {
                    "${it.service}:${it.user}:${it.id}:${it.hash}"
                  }
                } else {
                  searchState.dms.getOrNull(index)?.let {
                    "${it.service}:${it.user}:${it.id}:${it.hash}"
                  }
                }
            PagingAnchor(
                itemKey = itemKey,
                index = index,
                offset = listState.firstVisibleItemScrollOffset,
            )
          }
          .distinctUntilChanged()
          .collect { anchor ->
            if (searchState.query.isBlank()) {
              recentModel.onViewportChanged(anchor)
            } else {
              searchModel.onViewportChanged(anchor)
            }
          }
    }
    val navigationEffect =
        if (searchState.query.isBlank()) recentState.navigationEffect
        else searchState.paging.navigationEffect
    LaunchedEffect(navigationEffect, searchState.query.isBlank()) {
      when (val effect = navigationEffect) {
        is PagingEffect.ScrollToTop -> listState.scrollToItem(0)
        is PagingEffect.RestoreViewport ->
            listState.scrollToItem(effect.anchor.index + 1, effect.anchor.offset)
        is PagingEffect.RebaseSelection,
        null -> Unit
      }
    }
    DisposableEffect(onReselectHandlerChanged) {
      val handler = { latestOnReselect() }
      onReselectHandlerChanged(handler)
      onDispose { onReselectHandlerChanged(null) }
    }
    ErrorToastEffect(searchState.error?.localizedMessage())
    ErrorToastEffect(searchState.appendError?.localizedMessage())
    ErrorToastEffect(searchState.prependError?.localizedMessage())
    val recentAppendErrorMessage = recentState.appendError?.localizedMessage()
    val searchAppendErrorMessage = searchState.appendError?.localizedMessage()
    val recentPrependErrorMessage = recentState.prependError?.localizedMessage()
    val searchPrependErrorMessage = searchState.prependError?.localizedMessage()

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
        resultOfSuspend {
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
        PagedPullRefreshBox(
            currentPage =
                if (isSearchMode) searchState.visiblePageInfo?.currentPage ?: 1
                else recentState.visiblePageInfo?.currentPage ?: 1,
            enabled = if (isSearchMode) !searchState.isLoading else !recentState.loading,
            refreshing =
                if (isSearchMode) searchState.result.isRefreshing else recentState.refreshing,
            loadingPrevious =
                if (isSearchMode) searchState.isLoadingPrevious else recentState.isLoadingPrevious,
            onRefresh = refresh,
            onLoadPrevious = {
              if (isSearchMode) searchModel.loadPrevious() else recentModel.loadPrevious()
            },
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

            val canLoadPrevious =
                if (isSearchMode) searchState.canAutoLoadPrevious
                else recentState.canAutoLoadPrevious
            val loadingPrevious =
                if (isSearchMode) searchState.isLoadingPrevious else recentState.isLoadingPrevious
            previousPageHeader(
                canLoadPrevious = canLoadPrevious,
                loadingPrevious = loadingPrevious,
                errorMessage =
                    if (isSearchMode) searchPrependErrorMessage else recentPrependErrorMessage,
                onLoadPrevious = {
                  if (isSearchMode) searchModel.loadPrevious() else recentModel.loadPrevious()
                },
            )

            if (searchState.query.isBlank()) {
              if (recentState.loading && recentState.items.isEmpty()) {
                item(key = "recent-dms-skeleton") { ListLoadingSkeleton(itemHeightDp = 96) }
              } else {
                items(
                    recentState.items,
                    key = { "${it.service}:${it.user}:${it.hash}:${it.id}:${it.added}" },
                ) { dm ->
                  DmCard(
                      dm = dm,
                      translationState = dmTranslations[dm.translationKey()],
                      onTranslate = { translateDm(dm) },
                      onCreatorClick =
                          if (!dm.service.isNullOrBlank() && !dm.user.isNullOrBlank()) {
                            { openCreator(dm) }
                          } else null,
                  )
                }
                loadingFooter(
                    recentState.isLoadingMore,
                    recentAppendErrorMessage,
                )
              }
            } else if (searchState.isLoading && searchState.dms.isEmpty()) {
              item(key = "search-dms-skeleton") { ListLoadingSkeleton(itemHeightDp = 96) }
            } else {
              items(
                  searchState.dms,
                  key = { "${it.service}:${it.user}:${it.hash}:${it.id}:${it.added}" },
              ) { dm ->
                DmCard(
                    dm = dm,
                    translationState = dmTranslations[dm.translationKey()],
                    onTranslate = { translateDm(dm) },
                    onCreatorClick =
                        if (!dm.service.isNullOrBlank() && !dm.user.isNullOrBlank()) {
                          { openCreator(dm) }
                        } else null,
                )
              }
              loadingFooter(
                  searchState.isLoadingMore,
                  searchAppendErrorMessage,
              )
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
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
      }
    }

    ErrorToastEffect(recentState.error?.localizedMessage())
    ErrorToastEffect(recentState.appendError?.localizedMessage())
    if (searchState.query.isBlank()) {
      AutoLoadEffect(
          listState = listState,
          totalItems = recentState.items.size,
          hasMore = recentState.hasMore,
          isLoadingMore = recentState.isLoadingMore,
          onLoadMore = recentModel::loadMore,
      )
    } else {
      AutoLoadEffect(
          listState = listState,
          totalItems = searchState.dms.size,
          hasMore = searchState.hasMore,
          isLoadingMore = searchState.isLoadingMore,
          onLoadMore = searchModel::loadMore,
      )
    }
  }
}

private fun DM.translationKey(): DmKey = key

private fun buildDmTranslationBlocks(content: String): List<TranslationBlock> =
    content.split(Regex("""(?:\r?\n[ \t]*){2,}""")).mapNotNull { block ->
      val text = block.trim()
      if (text.isBlank()) null else TranslationBlock(originalHtml = text, sourceText = text)
    }
