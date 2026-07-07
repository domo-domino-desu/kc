package ddd.kc.ui.pages.discord

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import ddd.kc.data.model.DiscordChannel
import ddd.kc.data.model.DiscordPost
import ddd.kc.data.model.Platform
import ddd.kc.data.model.avatarUrl
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.isImage
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.AttachFileW400Outlined
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.components.AutoLoadEffect
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.KcPullRefreshBox
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.components.PageStateContent
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.components.loadingFooter
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.imageviewer.ImageViewerScreen
import ddd.kc.ui.state.PaginationSnapshot
import ddd.kc.utils.collapseConsecutiveBlankLines
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

class DiscordChannelRouteScreen(
    private val platform: Platform,
    private val channels: List<DiscordChannel>,
    private val startIndex: Int,
    private val routeKey: String = nextRouteInstanceKey("discord"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun Content() {
    val screenModel =
        koinScreenModel<DiscordChannelScreenModel> { parametersOf(platform, channels, startIndex) }
    val state by screenModel.state.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val pagerState =
        rememberPagerState(
            initialPage = startIndex,
            pageCount = { state.channels.size },
        )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(pagerState.currentPage) { screenModel.onPageChanged(pagerState.currentPage) }
    LaunchedEffect(state.currentIndex) {
      focusRequester.requestFocus()
      if (pagerState.currentPage != state.currentIndex) {
        pagerState.animateScrollToPage(state.currentIndex)
      }
    }

    Box(
        modifier =
            Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onPreviewKeyEvent {
                event ->
              if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (event.key) {
                Key.DirectionLeft -> {
                  screenModel.previous()
                  true
                }
                Key.DirectionRight -> {
                  screenModel.next()
                  true
                }
                else -> false
              }
            },
    ) {
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val channel = state.channels.getOrNull(page) ?: return@HorizontalPager
        LaunchedEffect(channel.id) { screenModel.loadChannelPosts(channel) }
        DiscordChannelPage(
            channel = channel,
            platform = platform,
            snapshot = screenModel.getSnapshot(channel),
            onLoadMore = { screenModel.loadMoreChannelPosts(channel) },
            onRefresh = { screenModel.loadChannelPosts(channel, forceRefresh = true) },
        )
      }
    }
  }
}

@Composable
private fun DiscordChannelPage(
    channel: DiscordChannel,
    platform: Platform,
    snapshot: PaginationSnapshot<DiscordPost>,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
  val navigator = LocalNavigator.currentOrThrow
  val appSettings = LocalAppSettings.current
  val cdnUrl = appSettings.cdnUrl(platform)
  val shareUrl = "${appSettings.baseUrl(platform)}/discord/channel/${channel.id}"
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val imageAspectRatios = remember(channel.id) { mutableStateMapOf<String, Float>() }

  // ④ 打平全频道所有消息的图片，供 ImageViewerScreen 左右滑动
  val allImageUrls =
      remember(snapshot.items, cdnUrl) {
        snapshot.items.flatMap { post ->
          post.attachments.filter { it.isImage() }.mapNotNull { it.fullUrl(cdnUrl) }
        }
      }
  val allThumbUrls =
      remember(snapshot.items, cdnUrl) {
        snapshot.items.flatMap { post ->
          post.attachments
              .filter { it.isImage() }
              .map { it.thumbnailUrl(cdnUrl) ?: it.fullUrl(cdnUrl) ?: "" }
        }
      }
  // 每条消息的图片在全局列表中的起始偏移
  val postImageOffsets =
      remember(snapshot.items) {
        var offset = 0
        snapshot.items.associate { post ->
          val count = post.attachments.count { it.isImage() }
          (post.id to offset).also { offset += count }
        }
      }

  Scaffold(
      topBar = {
        // ③ 回顶按钮
        DetailAppBar(
            title = "#${channel.name}",
            shareUrl = shareUrl,
            onScrollToTop = { scope.launch { listState.scrollToItem(0) } },
        )
      },
  ) { padding ->
    KcPullRefreshBox(
        enabled = !snapshot.loading,
        refreshing = snapshot.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      PageStateContent(snapshot = snapshot) {
        AutoLoadEffect(
            listState = listState,
            totalItems = snapshot.items.size,
            hasMore = snapshot.hasMore,
            isLoadingMore = snapshot.isLoadingMore,
            onLoadMore = onLoadMore,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(snapshot.items, key = { it.id }) { post ->
            DiscordMessageCard(
                post = post,
                cdnUrl = cdnUrl,
                globalImageOffset = postImageOffsets[post.id] ?: 0,
                imageAspectRatios = imageAspectRatios,
                onImageClick = { globalIdx ->
                  navigator.push(ImageViewerScreen(allImageUrls, allThumbUrls, globalIdx))
                },
            )
          }
          loadingFooter(snapshot.isLoadingMore, snapshot.appendErrorMessage)
          item { Spacer(Modifier.height(80.dp)) }
        }
      }
    }
  }
}

@Composable
private fun DiscordMessageCard(
    post: DiscordPost,
    cdnUrl: String,
    globalImageOffset: Int,
    imageAspectRatios: MutableMap<String, Float>,
    onImageClick: (globalIndex: Int) -> Unit,
) {
  Surface(
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Author row
      Row(verticalAlignment = Alignment.CenterVertically) {
        val avatarUrl = post.author.avatarUrl()
        if (avatarUrl != null) {
          NetworkImage(
              url = avatarUrl,
              modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
              contentScale = ContentScale.Crop,
          )
        } else {
          Box(
              modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
              contentAlignment = Alignment.Center,
          ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxSize(),
            ) {}
            Text(
                text = post.author.username.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = post.author.username.ifBlank { "Unknown" },
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              maxLines = 1,
          )
          val discriminator = post.author.discriminator
          if (!discriminator.isNullOrBlank() && discriminator != "0") {
            Text(
                text = "#$discriminator",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        val timestamp = post.published ?: post.added
        if (!timestamp.isNullOrBlank()) {
          Text(
              text = timestamp.take(16).replace('T', ' '),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Message text
      if (!post.content.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
          Text(
              text = collapseConsecutiveBlankLines(post.content),
              style = MaterialTheme.typography.bodyMedium,
          )
        }
      }

      val imageAttachments = post.attachments.filter { it.isImage() }
      val otherAttachments = post.attachments.filter { !it.isImage() }

      // Image attachments — ① 真实 aspect ratio，加载中显示 1:1 骨架
      if (imageAttachments.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        imageAttachments.forEachIndexed { idx, att ->
          AttachmentImage(
              thumbnailUrl = att.thumbnailUrl(cdnUrl),
              fullUrl = att.fullUrl(cdnUrl),
              aspectRatioCache = imageAspectRatios,
              onClick = { onImageClick(globalImageOffset + idx) },
          )
          if (idx < imageAttachments.lastIndex) Spacer(Modifier.height(4.dp))
        }
      }

      // Non-image attachments
      if (otherAttachments.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        otherAttachments.forEach { att ->
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 2.dp),
          ) {
            Icon(
                imageVector = Icons.AttachFileW400Outlined,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = att.name ?: att.path?.substringAfterLast('/') ?: "attachment",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

// ① 加载中显示 1:1 骨架屏，加载成功后切换为图片真实 aspect ratio。
// aspectRatioCache 由调用方持有（page 级别），避免 item 出屏后状态丢失导致滚动跳动。
@Composable
private fun AttachmentImage(
    thumbnailUrl: String?,
    fullUrl: String?,
    aspectRatioCache: MutableMap<String, Float>,
    onClick: () -> Unit,
) {
  val url = thumbnailUrl ?: fullUrl ?: return
  val aspectRatio = aspectRatioCache[url]
  SubcomposeAsyncImage(
      model = url,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier =
          Modifier.fillMaxWidth()
              .aspectRatio(aspectRatio ?: 1f)
              .clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onClick),
      onSuccess = { state ->
        val size = state.painter.intrinsicSize
        if (
            size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()
        ) {
          aspectRatioCache[url] = size.width / size.height
        }
      },
      loading = { SkeletonBlock(modifier = Modifier.fillMaxSize()) },
      error = {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
      },
      success = { SubcomposeAsyncImageContent() },
  )
}
