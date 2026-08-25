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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.CommunityMessage
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.bannerUrl
import ddd.kc.data.model.key
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400OutlinedFill
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ListAltW400Outlined
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.app.i18n.localizedMessage
import ddd.kc.ui.app.navigation.ImageViewerItem
import ddd.kc.ui.app.navigation.ImageWindow
import ddd.kc.ui.app.navigation.ImageWindowSnapshot
import ddd.kc.ui.app.navigation.LocalNavigationWindowStore
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.CreatorBannerFallback
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.KcPullRefreshBox
import ddd.kc.ui.components.ListLoadingSkeleton
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.components.PageJumpFabMenu
import ddd.kc.ui.components.PostCard
import ddd.kc.ui.components.PostGridPagingActions
import ddd.kc.ui.components.PostGridPagingControls
import ddd.kc.ui.components.PostGridPagingState
import ddd.kc.ui.components.ScrollableButtonGroup
import ddd.kc.ui.components.TranslateIconButton
import ddd.kc.ui.components.gridSkeletonItems
import ddd.kc.ui.components.icons.rememberServiceIconDefinition
import ddd.kc.ui.components.icons.serviceIconVector
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.components.paging.ContentTranslationState
import ddd.kc.ui.components.paging.PagingAnchor
import ddd.kc.ui.components.paging.PagingEffect
import ddd.kc.ui.components.paging.TranslationBlockState
import ddd.kc.ui.components.paging.TranslationStatus
import ddd.kc.ui.components.previousPageHeader
import ddd.kc.ui.pages.imageviewer.ImageViewerScreen
import ddd.kc.utils.collapseConsecutiveBlankLines
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.add_favorite
import kc.shared.generated.resources.community_creator_badge
import kc.shared.generated.resources.community_directory
import kc.shared.generated.resources.creator_tab_announcements
import kc.shared.generated.resources.creator_tab_community
import kc.shared.generated.resources.creator_tab_posts
import kc.shared.generated.resources.creator_tab_tags
import kc.shared.generated.resources.linked_accounts
import kc.shared.generated.resources.no_announcements
import kc.shared.generated.resources.no_community_messages
import kc.shared.generated.resources.no_tags
import kc.shared.generated.resources.remove_favorite
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private enum class CreatorContentTab {
  POSTS,
  ANNOUNCEMENTS,
  TAGS,
  COMMUNITY,
}

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
  val navigator = LocalNavigator.currentOrThrow
  val navigationWindows = LocalNavigationWindowStore.current
  val appSettings = LocalAppSettings.current
  val cdnUrl = appSettings.cdnUrl()
  val baseUrl = appSettings.baseUrl()
  val cellWidth = appSettings.cellMinWidthDp()
  var selectedTab by remember(creator.key) { mutableStateOf(CreatorContentTab.POSTS) }
  val gridState = rememberLazyGridState()
  val coroutineScope = rememberCoroutineScope()
  val postSnapshot = screenModel.getCreatorPostSnapshot(creator)
  val posts = screenModel.getCreatorPosts(creator)
  val announcements = screenModel.getCreatorAnnouncements(creator)
  val tags = screenModel.getCreatorTags(creator)
  val community = screenModel.getCreatorCommunity(creator)
  val communitySnapshot = community.selectedSnapshot
  val isFavorite = creator.key in state.favoriteCreatorIds
  val isLoadingPosts = creator.key in state.loadingCreatorPostIds
  val isLoadingAnnouncements = creator.key in state.loadingCreatorAnnouncementIds
  val isLoadingTags = creator.key in state.loadingCreatorTagIds
  val linkedCreators = screenModel.getCreatorLinks(creator)
  val announcementTranslations = state.announcementTranslations[creator.key] ?: emptyMap()
  val refresh = {
    when (selectedTab) {
      CreatorContentTab.POSTS -> screenModel.loadCreatorPosts(creator, forceRefresh = true)
      CreatorContentTab.ANNOUNCEMENTS ->
          screenModel.loadCreatorAnnouncements(creator, forceRefresh = true)
      CreatorContentTab.TAGS -> screenModel.loadCreatorTags(creator, forceRefresh = true)
      CreatorContentTab.COMMUNITY -> screenModel.refreshCreatorCommunity(creator)
    }
  }
  val isCurrentTabLoading =
      when (selectedTab) {
        CreatorContentTab.POSTS -> isLoadingPosts
        CreatorContentTab.ANNOUNCEMENTS -> isLoadingAnnouncements
        CreatorContentTab.TAGS -> isLoadingTags
        CreatorContentTab.COMMUNITY -> community.loading || communitySnapshot.loading
      }
  val hasCurrentTabContent =
      when (selectedTab) {
        CreatorContentTab.POSTS -> posts.isNotEmpty()
        CreatorContentTab.ANNOUNCEMENTS -> announcements.isNotEmpty()
        CreatorContentTab.TAGS -> tags.isNotEmpty()
        CreatorContentTab.COMMUNITY -> communitySnapshot.items.isNotEmpty()
      }
  ErrorToastEffect(state.favoriteError?.localizedMessage())
  ErrorToastEffect(state.announcementErrors[creator.key]?.localizedMessage())
  ErrorToastEffect(state.tagErrors[creator.key]?.localizedMessage())
  ErrorToastEffect(state.linkErrors[creator.key]?.localizedMessage())
  ErrorToastEffect(community.error?.localizedMessage())
  ErrorToastEffect(postSnapshot.appendError?.localizedMessage())
  ErrorToastEffect(postSnapshot.prependError?.localizedMessage())
  val postAppendErrorMessage = postSnapshot.appendError?.localizedMessage()
  val communityAppendErrorMessage = communitySnapshot.appendError?.localizedMessage()
  val communityPrependErrorMessage = communitySnapshot.prependError?.localizedMessage()
  val communityLeadingItemCount =
      if (communitySnapshot.canAutoLoadPrevious || communityPrependErrorMessage != null) 3 else 2

  LaunchedEffect(creator.service, creator.id) {
    screenModel.loadCreatorPosts(creator)
    screenModel.loadCreatorAnnouncements(creator)
    screenModel.loadCreatorTags(creator)
    screenModel.loadFavoriteStatus(creator)
    screenModel.loadCreatorLinks(creator)
    screenModel.loadCreatorCommunity(creator)
  }
  LaunchedEffect(gridState, selectedTab, communitySnapshot.items) {
    if (selectedTab != CreatorContentTab.COMMUNITY) return@LaunchedEffect
    snapshotFlow {
          val index = (gridState.firstVisibleItemIndex - communityLeadingItemCount).coerceAtLeast(0)
          PagingAnchor(
              itemKey = communitySnapshot.items.getOrNull(index)?.key,
              index = index,
              offset = gridState.firstVisibleItemScrollOffset,
          )
        }
        .distinctUntilChanged()
        .collect { screenModel.onCommunityViewportChanged(creator, it) }
  }
  LaunchedEffect(communitySnapshot.navigationEffect) {
    when (val effect = communitySnapshot.navigationEffect) {
      is PagingEffect.ScrollToTop -> {
        gridState.scrollToItem(0)
        screenModel.onCommunityNavigationEffectHandled(creator, effect.transactionId)
      }
      is PagingEffect.RestoreViewport -> {
        gridState.scrollToItem(
            effect.anchor.index + communityLeadingItemCount,
            effect.anchor.offset,
        )
        screenModel.onCommunityNavigationEffectHandled(creator, effect.transactionId)
      }
      is PagingEffect.RebaseSelection,
      null -> Unit
    }
  }

  val shareUrl = "$baseUrl/${creator.service}/user/${creator.id}"
  Scaffold(
      topBar = {
        DetailAppBar(
            title = creator.name,
            shareUrl = shareUrl,
            onScrollToTop = { coroutineScope.launch { gridState.animateScrollToItem(0) } },
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
                            if (isFavorite) Icons.FavoriteW400OutlinedFill
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
            val tabs = buildList {
              add(CreatorContentTab.POSTS to stringResource(Res.string.creator_tab_posts))
              add(
                  CreatorContentTab.ANNOUNCEMENTS to
                      stringResource(Res.string.creator_tab_announcements)
              )
              add(CreatorContentTab.TAGS to stringResource(Res.string.creator_tab_tags))
              if (community.available == true) {
                add(CreatorContentTab.COMMUNITY to stringResource(Res.string.creator_tab_community))
              }
            }
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp),
            ) {
              ScrollableButtonGroup(
                  labels = tabs.map { it.second },
                  selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
                  onSelected = { index -> selectedTab = tabs[index].first },
              )
              if (selectedTab == CreatorContentTab.COMMUNITY) {
                CommunityLoungeSelector(
                    lounges = community.lounges,
                    selectedLoungeId = community.selectedLoungeId,
                    onSelect = { screenModel.selectCommunityLounge(creator, it) },
                )
              }
            }
          }

          // ── Tab content ────────────────────────────────────────────────
          when (selectedTab) {
            CreatorContentTab.POSTS -> {
              if (isLoadingPosts && posts.isEmpty()) {
                gridSkeletonItems()
              } else {
                items(posts, key = { "${it.service}:${it.artistId ?: it.user}:${it.id}" }) { post ->
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
            CreatorContentTab.ANNOUNCEMENTS -> {
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
            CreatorContentTab.TAGS -> {
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
            CreatorContentTab.COMMUNITY -> {
              val messages = communitySnapshot.items
              previousPageHeader(
                  canLoadPrevious = communitySnapshot.canAutoLoadPrevious,
                  loadingPrevious = communitySnapshot.isLoadingPrevious,
                  errorMessage = communityPrependErrorMessage,
                  onLoadPrevious = { screenModel.loadPreviousCommunity(creator) },
              )
              if ((community.loading || communitySnapshot.loading) && messages.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  ListLoadingSkeleton(itemCount = 5, itemHeightDp = 72)
                }
              } else if (messages.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  Text(
                      stringResource(Res.string.no_community_messages),
                      modifier = Modifier.padding(16.dp),
                  )
                }
              } else {
                items(
                    messages,
                    key = CommunityMessage::key,
                    span = { GridItemSpan(maxLineSpan) },
                ) { message ->
                  CommunityMessageItem(
                      message = message,
                      baseUrl = baseUrl,
                      onImageClick = imageClick@{
                            val loungeId = community.selectedLoungeId ?: return@imageClick
                            val initialSnapshot = communitySnapshot.toImageWindowSnapshot(baseUrl)
                            val imageKey = communityImageKey(message)
                            val startIndex =
                                initialSnapshot.images.indexOfFirst { it.key == imageKey }
                            if (startIndex < 0) return@imageClick
                            val snapshots =
                                screenModel.state
                                    .map { pagerState ->
                                      pagerState.creatorCommunities[creator.key]
                                          ?.loungeSnapshots
                                          ?.get(loungeId)
                                          ?.toImageWindowSnapshot(baseUrl) ?: initialSnapshot
                                    }
                                    .distinctUntilChanged()
                            navigator.push(
                                ImageViewerScreen(
                                    windowId =
                                        navigationWindows.putImageWindow(
                                            ImageWindow(
                                                initialSnapshot = initialSnapshot,
                                                snapshots = snapshots,
                                                onImageViewed = null,
                                                onLoadPrevious = {
                                                  screenModel.loadPreviousCommunity(
                                                      creator,
                                                      loungeId,
                                                  )
                                                },
                                                onLoadNext = {
                                                  screenModel.loadMoreCommunity(
                                                      creator,
                                                      loungeId,
                                                      retry = true,
                                                  )
                                                },
                                            )
                                        ),
                                    startIndex = startIndex,
                                )
                            )
                          },
                  )
                }
                loadingFooter(
                    communitySnapshot.isLoadingMore,
                    communityAppendErrorMessage,
                )
              }
            }
          }
        }
        if (selectedTab == CreatorContentTab.POSTS) {
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
                      prependErrorMessage = postSnapshot.prependError?.localizedMessage(),
                      appendErrorMessage = postSnapshot.appendError?.localizedMessage(),
                      navigationEffect = postSnapshot.navigationEffect,
                  ),
              actions =
                  PostGridPagingActions(
                      onRefresh = refresh,
                      onLoadMore = { screenModel.loadMoreCreatorPosts(creator) },
                      onLoadPrevious = { screenModel.loadPreviousCreatorPosts(creator) },
                      onJumpToPage = { page -> screenModel.jumpCreatorPostsToPage(creator, page) },
                      onViewportChanged = { anchor ->
                        screenModel.onCreatorPostViewportChanged(creator, anchor)
                      },
                      onPostClick = {},
                  ),
              gridState = gridState,
              leadingItemCount = 2,
          )
        } else if (selectedTab == CreatorContentTab.COMMUNITY) {
          AutoLoadEffect(
              gridState,
              communitySnapshot.items.size,
              hasMore = communitySnapshot.hasMore,
              isLoadingMore = communitySnapshot.isLoadingMore,
              onLoadMore = { screenModel.loadMoreCommunity(creator) },
          )
          PageJumpFabMenu(
              pageInfo = communitySnapshot.visiblePageInfo,
              loading =
                  communitySnapshot.loading ||
                      communitySnapshot.isLoadingMore ||
                      communitySnapshot.isLoadingPrevious,
              onJumpToPage = { screenModel.jumpCommunityToPage(creator, it) },
              modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun CommunityLoungeSelector(
    lounges: List<ddd.kc.data.model.CommunityLounge>,
    selectedLoungeId: String?,
    onSelect: (String) -> Unit,
) {
  val listState = rememberLazyListState()
  val selectedIndex = lounges.indexOfFirst { it.id == selectedLoungeId }
  var directoryExpanded by remember { mutableStateOf(false) }
  LaunchedEffect(selectedIndex) {
    if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
  }
  Row(
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    LazyRow(
        state = listState,
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
      itemsIndexed(lounges, key = { _, lounge -> lounge.id }) { _, lounge ->
        FilterChip(
            selected = lounge.id == selectedLoungeId,
            onClick = { onSelect(lounge.id) },
            label = { Text("${lounge.name} · ${lounge.messageCount}") },
        )
      }
    }
    Box {
      FilledTonalIconButton(onClick = { directoryExpanded = true }) {
        Icon(
            imageVector = Icons.ListAltW400Outlined,
            contentDescription = stringResource(Res.string.community_directory),
        )
      }
      DropdownMenu(
          expanded = directoryExpanded,
          onDismissRequest = { directoryExpanded = false },
      ) {
        lounges.forEach { lounge ->
          DropdownMenuItem(
              text = { Text("${lounge.name} · ${lounge.messageCount}") },
              onClick = {
                directoryExpanded = false
                onSelect(lounge.id)
              },
          )
        }
      }
    }
  }
}

@Composable
private fun CommunityMessageItem(
    message: CommunityMessage,
    baseUrl: String,
    onImageClick: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top,
  ) {
    Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.TopCenter) {
      if (message.startsGroup) {
        val avatarUrl =
            message.avatarUrl?.let { if (it.startsWith("http")) it else baseUrl.trimEnd('/') + it }
        if (avatarUrl != null) {
          NetworkImage(url = avatarUrl, modifier = Modifier.size(40.dp).clip(CircleShape))
        } else {
          Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.secondaryContainer,
              modifier = Modifier.size(40.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                  message.avatarInitial ?: message.author?.take(1).orEmpty(),
                  fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      message.reply?.let { reply ->
        Text(
            text = "↪ ${reply.author}  ${reply.text}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
      }
      if (message.startsGroup) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = message.author.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (message.isCreator) {
              Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                    text = stringResource(Res.string.community_creator_badge),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
              }
            }
          }
          message.published?.let {
            Text(
                text = it.replace('T', ' ').take(16),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      if (message.content.isNotBlank()) {
        val text = remember(message.content) { htmlToAnnotatedString(message.content) }
        SelectionContainer {
          Text(
              text = text,
              style = MaterialTheme.typography.bodyMedium,
              color =
                  if (message.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant
                  else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
      message.imageUrl?.let { imageUrl ->
        NetworkImage(
            url = imageUrl,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onImageClick),
            contentScale = ContentScale.Fit,
        )
      }
      message.embed?.let { embed ->
        Surface(
            onClick = { uriHandler.openUri(embed.url) },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            embed.site?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            embed.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            embed.description?.let {
              Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

private fun ddd.kc.ui.components.paging.OffsetPagingState<CommunityMessage>.toImageWindowSnapshot(
    baseUrl: String
): ImageWindowSnapshot =
    ImageWindowSnapshot(
        images =
            items.mapNotNull { message ->
              val imageUrl = message.imageUrl?.resolveAgainst(baseUrl) ?: return@mapNotNull null
              ImageViewerItem(
                  key = communityImageKey(message),
                  imageUrl = imageUrl,
                  thumbnailUrl = imageUrl,
              )
            },
        hasPrevious = hasPrevious,
        hasNext = hasMore,
        isLoadingPrevious = isLoadingPrevious,
        isLoadingNext = isLoadingMore,
        previousError = prependError,
        nextError = appendError,
    )

private fun communityImageKey(message: CommunityMessage): String = "${message.key}:image"

private fun String.resolveAgainst(baseUrl: String): String =
    if (startsWith("http://") || startsWith("https://")) this
    else "${baseUrl.trimEnd('/')}/${trimStart('/')}"

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
