package ddd.kc.ui.pages.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import ddd.kc.LocalActivePlatform
import ddd.kc.application.translation.TranslationService
import ddd.kc.data.model.DM
import ddd.kc.domain.translation.TranslationBlock
import ddd.kc.domain.translation.TranslationBlockResult
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.DmCard
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.pages.recent.RecentDMsScreenModel
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.TranslationBlockState
import ddd.kc.ui.state.TranslationStatus
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.search_dms_hint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class DmScreen(private val repeatSelectionToken: Int = 0) : Screen {
  @Composable
  override fun Content() {
    val platform = LocalActivePlatform.current
    val searchModel = koinInject<DmSearchScreenModel>()
    val recentModel = koinInject<RecentDMsScreenModel>()
    val translationService = koinInject<TranslationService>()
    val searchState by searchModel.state.collectAsState()
    val recentState by recentModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastHandledRepeatToken by remember { mutableIntStateOf(repeatSelectionToken) }
    val dmTranslations = remember(platform) { mutableStateMapOf<String, ContentTranslationState>() }

    LaunchedEffect(platform) {
      searchModel.init(platform)
      recentModel.load(platform)
    }
    LaunchedEffect(repeatSelectionToken) {
      if (repeatSelectionToken == lastHandledRepeatToken) return@LaunchedEffect
      lastHandledRepeatToken = repeatSelectionToken
      if (listState.isAtTop) {
        if (searchState.query.isBlank()) {
          recentModel.load(platform, forceRefresh = true)
        } else {
          searchModel.refresh()
        }
      } else {
        scope.launch { listState.animateScrollToItem(0) }
      }
    }
    ErrorToastEffect(searchState.errorMessage)

    val translateDm: (DM) -> Unit = translateDm@{ dm ->
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

    Scaffold { paddingValues ->
      LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().padding(paddingValues),
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
        }
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
