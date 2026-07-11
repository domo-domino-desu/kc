package ddd.kc.ui.pages.creator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.bannerUrl
import ddd.kc.data.model.key
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlinedfill1
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.app.i18n.localizedMessage
import ddd.kc.ui.components.CreatorBannerFallback
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.KcPullRefreshBox
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.components.PostCard
import ddd.kc.ui.components.PostGridPagingActions
import ddd.kc.ui.components.PostGridPagingControls
import ddd.kc.ui.components.PostGridPagingState
import ddd.kc.ui.components.TranslateIconButton
import ddd.kc.ui.components.gridSkeletonItems
import ddd.kc.ui.components.icons.rememberServiceIconDefinition
import ddd.kc.ui.components.icons.serviceIconVector
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.components.state.ContentTranslationState
import ddd.kc.ui.components.state.TranslationBlockState
import ddd.kc.ui.components.state.TranslationStatus
import ddd.kc.utils.collapseConsecutiveBlankLines
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.add_favorite
import kc.shared.generated.resources.creator_tab_announcements
import kc.shared.generated.resources.creator_tab_posts
import kc.shared.generated.resources.creator_tab_tags
import kc.shared.generated.resources.linked_accounts
import kc.shared.generated.resources.no_announcements
import kc.shared.generated.resources.no_tags
import kc.shared.generated.resources.remove_favorite
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun CreatorDetailPage(
    creator: Creator,
    state: CreatorPagerUiState,
    screenModel: CreatorScreenModel,
    onPostClick: (Post, List<Post>, Int, Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onCreatorListOpen: (String, List<Creator>, Boolean) -> Unit,
) {
  val appSettings = LocalAppSettings.current
  val cdnUrl = appSettings.cdnUrl()
  val baseUrl = appSettings.baseUrl()
  val cellWidth = appSettings.cellMinWidthDp()
  var selectedTab by remember(creator.key) { mutableIntStateOf(0) }
  val gridState = rememberLazyGridState()
  val postSnapshot = screenModel.getCreatorPostSnapshot(creator)
  val posts = screenModel.getCreatorPosts(creator)
  val announcements = screenModel.getCreatorAnnouncements(creator)
  val tags = screenModel.getCreatorTags(creator)
  val isFavorite = creator.key in state.favoriteCreatorIds
  val isLoadingPosts = creator.key in state.loadingCreatorPostIds
  val isLoadingAnnouncements = creator.key in state.loadingCreatorAnnouncementIds
  val isLoadingTags = creator.key in state.loadingCreatorTagIds
  val linkedCreators = screenModel.getCreatorLinks(creator)
  val announcementTranslations = state.announcementTranslations[creator.key] ?: emptyMap()
  val refresh = {
    when (selectedTab) {
      0 -> screenModel.loadCreatorPosts(creator, forceRefresh = true)
      1 -> screenModel.loadCreatorAnnouncements(creator, forceRefresh = true)
      2 -> screenModel.loadCreatorTags(creator, forceRefresh = true)
    }
  }
  val isCurrentTabLoading =
      when (selectedTab) {
        0 -> isLoadingPosts
        1 -> isLoadingAnnouncements
        2 -> isLoadingTags
        else -> false
      }
  val hasCurrentTabContent =
      when (selectedTab) {
        0 -> posts.isNotEmpty()
        1 -> announcements.isNotEmpty()
        2 -> tags.isNotEmpty()
        else -> false
      }
  ErrorToastEffect(state.favoriteError?.localizedMessage())
  ErrorToastEffect(state.announcementErrors[creator.key]?.localizedMessage())
  ErrorToastEffect(state.tagErrors[creator.key]?.localizedMessage())
  ErrorToastEffect(state.linkErrors[creator.key]?.localizedMessage())
  ErrorToastEffect(postSnapshot.appendError?.localizedMessage())
  ErrorToastEffect(postSnapshot.prependError?.localizedMessage())
  val postAppendErrorMessage = postSnapshot.appendError?.localizedMessage()

  LaunchedEffect(creator.service, creator.id) {
    screenModel.loadCreatorPosts(creator)
    screenModel.loadCreatorAnnouncements(creator)
    screenModel.loadCreatorTags(creator)
    screenModel.loadFavoriteStatus(creator)
    screenModel.loadCreatorLinks(creator)
  }

  val shareUrl = "$baseUrl/${creator.service}/user/${creator.id}"
  if (selectedTab == 0) {
    LaunchedEffect(gridState, creator.key, selectedTab) {
      snapshotFlow { gridState.firstVisibleItemIndex }
          .distinctUntilChanged()
          .collect { index -> screenModel.onCreatorPostVisibleIndex(creator, index - 2) }
    }
  }
  Scaffold(
      topBar = {
        DetailAppBar(
            title = creator.name,
            shareUrl = shareUrl,
        )
      },
  ) { paddingValues ->
    KcPullRefreshBox(
        enabled = !isCurrentTabLoading,
        refreshing = isCurrentTabLoading && hasCurrentTabContent,
        onRefresh = refresh,
        modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cellWidth.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // ── Creator card ───────────────────────────────────────────────
          item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
              Column {
                val avatarUrl = creator.thumbnailUrl(baseUrl)
                // Banner
                NetworkImage(
                    url = creator.bannerUrl(baseUrl),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentScale = ContentScale.Crop,
                    fallbackContent = {
                      CreatorBannerFallback(
                          avatarSeed = avatarUrl.ifBlank { "${creator.service}:${creator.id}" }
                      )
                    },
                    logFailureAsWarning = false,
                )
                // Avatar + name + subscribe row
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  // Avatar
                  Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.secondaryContainer,
                  ) {
                    NetworkImage(
                        url = avatarUrl,
                        modifier = Modifier.size(54.dp).clip(CircleShape),
                    )
                  }
                  Spacer(Modifier.width(12.dp))
                  // Name + chips
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = creator.name,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      // Service pill (platform)
                      CreatorPill(text = creator.service, service = creator.service) {}
                      if (linkedCreators.isNotEmpty()) {
                        val linkedLabel =
                            stringResource(Res.string.linked_accounts, linkedCreators.size)
                        CreatorPill(text = linkedLabel) {
                          onCreatorListOpen(linkedLabel, linkedCreators, false)
                        }
                      }
                    }
                  }
                  Spacer(Modifier.width(8.dp))
                  // Subscribe / Favorite button
                  FilledTonalIconButton(
                      onClick = { screenModel.toggleFavoriteCreator(creator) },
                      modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                        imageVector =
                            if (isFavorite) Icons.FavoriteW400Outlinedfill1
                            else Icons.FavoriteW400Outlined,
                        contentDescription =
                            if (isFavorite) stringResource(Res.string.remove_favorite)
                            else stringResource(Res.string.add_favorite),
                    )
                  }
                }
              }
            }
          }

          // ── Tab row ────────────────────────────────────────────────────
          stickyHeader {
            val tabLabels =
                listOf(
                    stringResource(Res.string.creator_tab_posts),
                    stringResource(Res.string.creator_tab_announcements),
                    stringResource(Res.string.creator_tab_tags),
                )
            ButtonGroup(
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                horizontalArrangement =
                    Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp),
            ) {
              tabLabels.forEachIndexed { index, label ->
                toggleableItem(
                    checked = selectedTab == index,
                    label = label,
                    onCheckedChange = { selectedTab = index },
                    weight = 1f,
                )
              }
            }
          }

          // ── Tab content ────────────────────────────────────────────────
          when (selectedTab) {
            0 -> {
              if (isLoadingPosts && posts.isEmpty()) {
                gridSkeletonItems()
              } else {
                items(posts, key = { it.key }) { post ->
                  PostCard(
                      post = post,
                      onClick = {
                        onPostClick(post, posts, postSnapshot.startOffset, postSnapshot.hasMore)
                      },
                  )
                }
                loadingFooter(
                    postSnapshot.isLoadingMore,
                    postAppendErrorMessage,
                )
              }
            }
            1 -> {
              if (isLoadingAnnouncements && announcements.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  ListLoadingSkeleton(itemCount = 3, itemHeightDp = 88)
                }
              } else if (announcements.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  Text(
                      stringResource(Res.string.no_announcements),
                      modifier = Modifier.padding(16.dp),
                  )
                }
              } else {
                announcements.forEach { announcement ->
                  item(
                      key =
                          announcement.hash.ifBlank {
                            "${announcement.service}:${announcement.userId}:${announcement.added}"
                          },
                      span = { GridItemSpan(maxLineSpan) },
                  ) {
                    AnnouncementCard(
                        announcement = announcement,
                        translationState = announcementTranslations[announcement.translationKey()],
                        onTranslate = { screenModel.translateAnnouncement(creator, announcement) },
                    )
                  }
                }
              }
            }
            2 -> {
              item(span = { GridItemSpan(maxLineSpan) }) {
                val tags = screenModel.getCreatorTags(creator)
                if (isLoadingTags && tags.isEmpty()) {
                  ListLoadingSkeleton(itemCount = 3, itemHeightDp = 44)
                } else if (tags.isEmpty()) {
                  Text(stringResource(Res.string.no_tags), modifier = Modifier.padding(16.dp))
                } else {
                  FlowRow(
                      modifier = Modifier.padding(16.dp),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    tags.forEach { tag ->
                      AssistChip(
                          onClick = { onTagClick(tag.tag) },
                          label = { Text(tag.tag) },
                      )
                    }
                  }
                }
              }
            }
          }
        }
        if (selectedTab == 0) {
          PostGridPagingControls(
              state =
                  PostGridPagingState(
                      posts = posts,
                      visiblePageInfo = postSnapshot.visiblePageInfo,
                      loading = postSnapshot.loading,
                      refreshing = postSnapshot.refreshing,
                      isLoadingMore = postSnapshot.isLoadingMore,
                      isLoadingPrevious = postSnapshot.isLoadingPrevious,
                      hasMore = postSnapshot.hasMore,
                      canLoadPrevious = postSnapshot.canAutoLoadPrevious,
                      appendErrorMessage = postSnapshot.appendError?.localizedMessage(),
                  ),
              actions =
                  PostGridPagingActions(
                      onRefresh = refresh,
                      onLoadMore = { screenModel.loadMoreCreatorPosts(creator) },
                      onLoadPrevious = { screenModel.loadPreviousCreatorPosts(creator) },
                      onJumpToPage = { page -> screenModel.jumpCreatorPostsToPage(creator, page) },
                      onVisiblePostIndex = { index ->
                        screenModel.onCreatorPostVisibleIndex(creator, index)
                      },
                      onPostClick = {},
                  ),
              gridState = gridState,
              leadingItemCount = 2,
          )
        }
      }
    }
  }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    translationState: ContentTranslationState?,
    onTranslate: () -> Unit,
) {
  Surface(
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(8.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Start,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!announcement.added.isNullOrBlank()) {
          Text(
              text = announcement.added.take(10),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (announcement.content.isNotBlank()) {
          if (!announcement.added.isNullOrBlank()) Spacer(Modifier.width(4.dp))
          TranslateIconButton(
              onClick = onTranslate,
              isTranslating = translationState?.isTranslating == true,
              isActive = translationState?.showTranslation == true,
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      if (translationState?.showTranslation == true && translationState.blocks.isNotEmpty()) {
        Column {
          translationState.blocks.forEachIndexed { index, block ->
            AnnouncementTranslatedBlockItem(
                block = block,
                showDivider = index < translationState.blocks.lastIndex,
            )
          }
        }
      } else {
        val html =
            remember(announcement.content) {
              htmlToAnnotatedString(collapseConsecutiveBlankLines(announcement.content))
            }
        SelectionContainer {
          Text(
              text = html,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun AnnouncementTranslatedBlockItem(block: TranslationBlockState, showDivider: Boolean) {
  val originalText =
      remember(block.originalHtml) {
        htmlToAnnotatedString(collapseConsecutiveBlankLines(block.originalHtml))
      }
  SelectionContainer {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      Text(
          text = originalText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(6.dp))
      Text(
          text =
              when (block.status) {
                TranslationStatus.PENDING -> "……"
                TranslationStatus.SUCCESS ->
                    collapseConsecutiveBlankLines(block.translated.orEmpty())
                TranslationStatus.EMPTY -> ""
                TranslationStatus.FAILURE -> "翻译失败"
                TranslationStatus.IDLE -> ""
              },
          style = MaterialTheme.typography.bodyMedium,
          color =
              if (block.status == TranslationStatus.FAILURE) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
      )
      if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
      }
    }
  }
}

@Composable
private fun CreatorPill(text: String, service: String? = null, onClick: () -> Unit) {
  val serviceIcon = service?.let { rememberServiceIconDefinition(it) }
  val imageVector = serviceIconVector(serviceIcon?.icon)
  Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
      modifier = Modifier.clickable(onClick = onClick),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
      if (imageVector != null) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp),
        )
      }
      Text(
          text = serviceIcon?.label ?: text,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
