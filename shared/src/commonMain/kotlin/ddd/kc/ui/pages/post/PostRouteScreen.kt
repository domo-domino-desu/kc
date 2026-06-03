package ddd.kc.ui.pages.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.imageFiles
import ddd.kc.data.model.isImage
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.AttachFileW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CalendarAddOnW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CommentW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.DateRangeW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.DownloadW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlinedfill1
import ddd.kc.generated.symbols.icons.materialsymbols.icons.LinkW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.TagW400Outlined
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.imageviewer.ImageViewerScreen
import ddd.kc.ui.pages.tagposts.TagPostsScreen
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.TranslationBlockState
import ddd.kc.ui.state.TranslationStatus
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import ddd.kc.util.logging.summarizePostFiles
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.comments_count
import kc.shared.generated.resources.no_comments
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

private val log = KcLog.withTag("PostRouteScreen")

class PostRouteScreen(
    private val platform: Platform,
    private val posts: List<Post>,
    private val startIndex: Int,
    private val source: String = "unknown",
    private val routeKey: String = nextRouteInstanceKey("post"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<PostScreenModel> { parametersOf(platform, posts, startIndex) }
    val state by screenModel.state.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val pagerState =
        rememberPagerState(
            initialPage = startIndex,
            pageCount = { state.posts.size },
        )

    LaunchedEffect(Unit) {
      log.i {
        "打开Post -> 进入页面(source=$source,platform=${platform.name},startIndex=$startIndex,count=${posts.size})"
      }
      posts.getOrNull(startIndex)?.let {
        log.i { "打开Post -> 初始Post(${summarizePost(it)},${summarizePostFiles(it)})" }
      }
      focusRequester.requestFocus()
    }
    LaunchedEffect(pagerState.currentPage) { screenModel.onPageChanged(pagerState.currentPage) }
    LaunchedEffect(state.currentIndex) {
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
            }
    ) {
      val cdnUrl = LocalAppSettings.current.cdnUrl(platform)
      val scope = rememberCoroutineScope()
      // Tracks each page's LazyListState for scroll-to-top
      val pageListStates = remember { mutableStateOf<Map<Int, LazyListState>>(emptyMap()) }

      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val post = state.posts.getOrNull(page) ?: return@HorizontalPager
        val listState = rememberLazyListState()
        LaunchedEffect(page) { pageListStates.value = pageListStates.value + (page to listState) }
        PostDetailPage(
            post = post,
            platform = platform,
            cdnUrl = cdnUrl,
            listState = listState,
            isFavorite = post.id in state.favoritePostIds,
            favoriteErrorMessage = state.favoriteErrorMessage,
            isDetailLoading = post.id in state.loadingDetailPostIds,
            creator = state.postCreators["${post.service}:${post.creatorId}"],
            comments = screenModel.getComments(post),
            translationState = state.postTranslations[post.id] ?: ContentTranslationState(),
            onFavoriteClick = { screenModel.toggleFavoritePost(post) },
            onTranslate = { screenModel.translateContent(post) },
            onTagClick = { tag -> navigator.push(TagPostsScreen(platform, tag)) },
            onArtistClick = {
              val creator = state.postCreators["${post.service}:${post.creatorId}"]
              if (creator != null) navigator.push(CreatorRouteScreen(platform, listOf(creator), 0))
            },
            onScrollToTop = {
              scope.launch { pageListStates.value[pagerState.currentPage]?.scrollToItem(0) }
            },
            onImageClick = { imageIndex ->
              val images = post.imageFiles()
              val urls = images.mapNotNull { it.fullUrl(cdnUrl) }
              val thumbs = images.mapNotNull { it.thumbnailUrl(cdnUrl) }
              log.i {
                "打开图片 -> 点击Post图片(platform=${platform.name},service=${post.service},creator=${post.user},post=${post.id},index=$imageIndex,count=${urls.size})"
              }
              navigator.push(ImageViewerScreen(urls, thumbs, imageIndex))
            },
        )
        LaunchedEffect(post.id) {
          screenModel.loadFavoriteStatus(post)
          screenModel.loadCreatorInfo(post)
          screenModel.loadComments(post)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PostDetailPage(
    post: Post,
    platform: Platform,
    cdnUrl: String,
    listState: LazyListState,
    isFavorite: Boolean,
    favoriteErrorMessage: String?,
    isDetailLoading: Boolean,
    creator: Creator?,
    comments: List<Comment>,
    translationState: ContentTranslationState,
    onFavoriteClick: () -> Unit,
    onTranslate: () -> Unit,
    onTagClick: (String) -> Unit,
    onArtistClick: () -> Unit,
    onScrollToTop: () -> Unit,
    onImageClick: (Int) -> Unit,
) {
  val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
  val cardShape = RoundedCornerShape(14.dp)
  val scope = rememberCoroutineScope()
  // page 级别缓存，key=url，避免 item 出屏后状态丢失导致滚动跳动
  val imageAspectRatios = remember(post.id) { mutableStateMapOf<String, Float>() }
  ErrorToastEffect(favoriteErrorMessage)

  Scaffold(
      topBar = {
        val shareUrl =
            "${platform.defaultBaseUrl}/${post.service}/user/${post.creatorId}/post/${post.id}"
        DetailAppBar(
            shareUrl = shareUrl,
            onTranslate = if (!post.content.isNullOrBlank()) onTranslate else null,
            isTranslating = translationState.isTranslating,
            isTranslateActive = translationState.showTranslation,
            onScrollToTop = onScrollToTop,
        )
      },
      floatingActionButton = {
        FloatingActionButton(onClick = onFavoriteClick) {
          Icon(
              imageVector =
                  if (isFavorite) Icons.FavoriteW400Outlinedfill1 else Icons.FavoriteW400Outlined,
              contentDescription = null,
          )
        }
      },
  ) { paddingValues ->
    val imageFiles = remember(post) { post.imageFiles() }
    val otherFiles = remember(post) { post.allFiles().filter { !it.isImage() } }
    val commentsItemIndex =
        remember(
            post.title,
            post.tags,
            post.content,
            imageFiles.size,
            otherFiles.size,
            post.embed,
        ) {
          var index = 0
          if (!post.title.isNullOrBlank()) index += 1
          index += 1 // author
          index += 1 // metadata
          index += 1 // tags / skeleton placeholder
          if (!post.content.isNullOrBlank()) index += 1
          index += imageFiles.size
          if (otherFiles.isNotEmpty()) index += 1
          val embed = post.embed
          if (embed != null && !embed.url.isNullOrBlank()) index += 1
          index
        }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      // ── Title ──────────────────────────────────────────────────────
      if (!post.title.isNullOrBlank()) {
        item {
          Text(
              text = post.title,
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
              modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
          )
        }
      }

      // ── Author info (click → artist page) ─────────────────────────
      item {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onArtistClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          if (creator != null) {
            NetworkImage(
                url = creator.thumbnailUrl(cdnUrl),
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
          } else {
            SkeletonBlock(modifier = Modifier.size(36.dp), shape = CircleShape)
          }
          Spacer(Modifier.width(10.dp))
          Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            if (creator != null) {
              Text(
                  text = creator.name,
                  style = MaterialTheme.typography.titleSmall,
              )
            } else {
              SkeletonBlock(
                  modifier = Modifier.fillMaxWidth(0.62f).height(18.dp),
                  shape = RoundedCornerShape(999.dp),
              )
            }
            Text(
                text = post.service,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        if (isDetailLoading) {
          PostInfoSkeleton()
        } else {
          FlowRow(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (!post.published.isNullOrBlank()) {
              PostInfoMetric(
                  icon = Icons.DateRangeW400Outlined,
                  text = post.published.take(10),
              )
            }
            if (!post.added.isNullOrBlank()) {
              PostInfoMetric(
                  icon = Icons.CalendarAddOnW400Outlined,
                  text = post.added.take(10),
              )
            }
            PostInfoMetric(
                icon = Icons.CommentW400Outlined,
                text = comments.size.toString(),
                onClick = { scope.launch { listState.animateScrollToItem(commentsItemIndex) } },
            )
            if (post.allFiles().isNotEmpty()) {
              PostInfoMetric(
                  icon = Icons.AttachFileW400Outlined,
                  text = post.allFiles().size.toString(),
              )
            }
          }
        }
      }

      // ── Tags ───────────────────────────────────────────────────────
      val tags = post.tags
      item {
        if (isDetailLoading && tags.isNullOrEmpty()) {
          PostTagsSkeleton()
        } else if (!tags.isNullOrEmpty()) {
          FlowRow(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            tags.forEach { tag ->
              Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                  border =
                      BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                      ),
                  modifier = Modifier.clickable { onTagClick(tag) },
              ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                  Icon(
                      imageVector = Icons.TagW400Outlined,
                      contentDescription = null,
                      modifier = Modifier.size(12.dp),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(Modifier.width(3.dp))
                  Text(tag, style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }

      // ── HTML content ───────────────────────────────────────────────
      if (!post.content.isNullOrBlank()) {
        item {
          if (translationState.showTranslation && translationState.blocks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
              translationState.blocks.forEachIndexed { index, block ->
                TranslatedBlockItem(
                    block = block,
                    showDivider = index < translationState.blocks.lastIndex,
                )
              }
            }
          } else {
            val htmlText = remember(post.content) { htmlToAnnotatedString(post.content) }
            Text(
                text = htmlText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
          }
        }
      }

      // ── Images ────────────────────────────────────────────────────
      itemsIndexed(imageFiles, key = { idx, _ -> "img-$idx" }) { idx, file ->
        PostAttachmentImage(
            thumbnailUrl = file.thumbnailUrl(cdnUrl),
            fullUrl = file.fullUrl(cdnUrl),
            contentDescription = file.name,
            aspectRatioCache = imageAspectRatios,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            shape = cardShape,
            onClick = { onImageClick(idx) },
        )
      }

      // ── Non-image attachments (in card) ───────────────────────────
      if (otherFiles.isNotEmpty()) {
        item {
          Surface(
              shape = cardShape,
              border = cardBorder,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
          ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
              otherFiles.forEach { file -> AttachmentRow(file = file, cdnUrl = cdnUrl) }
            }
          }
        }
      }

      // ── Embed (in card) ───────────────────────────────────────────
      val embed = post.embed
      if (embed != null && !embed.url.isNullOrBlank()) {
        item {
          Surface(
              shape = cardShape,
              border = cardBorder,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
          ) {
            EmbedRow(url = embed.url, subject = embed.subject, description = embed.description)
          }
        }
      }

      // ── Comments ──────────────────────────────────────────────────
      item {
        Surface(
            shape = cardShape,
            border = cardBorder,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(Res.string.comments_count, comments.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (comments.isEmpty()) {
              Text(
                  text = stringResource(Res.string.no_comments),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      itemsIndexed(comments, key = { _, comment -> "comment-${comment.id}" }) { _, comment ->
        val parentId = comment.parentId?.takeIf { it.isNotBlank() }
        val parentIndex = parentId?.let { id -> comments.indexOfFirst { it.id == id } } ?: -1
        CommentRow(
            comment = comment,
            onParentClick =
                if (parentIndex >= 0) {
                  {
                    scope.launch {
                      listState.animateScrollToItem(commentsItemIndex + 1 + parentIndex)
                    }
                  }
                } else {
                  null
                },
        )
      }

      item { Spacer(Modifier.height(80.dp)) }
    }
  }
}

@Composable
private fun TranslatedBlockItem(block: TranslationBlockState, showDivider: Boolean) {
  val originalText = remember(block.originalHtml) { htmlToAnnotatedString(block.originalHtml) }
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
                TranslationStatus.SUCCESS -> block.translated.orEmpty()
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
private fun PostInfoMetric(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(12.dp),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun PostInfoSkeleton() {
  FlowRow(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    repeat(4) { index ->
      SkeletonBlock(
          modifier = Modifier.width((72 + index * 10).dp).height(18.dp),
          shape = RoundedCornerShape(999.dp),
      )
    }
  }
}

@Composable
private fun PostTagsSkeleton() {
  FlowRow(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    repeat(3) { index ->
      SkeletonBlock(
          modifier = Modifier.width((58 + (index * 17 % 64)).dp).height(24.dp),
          shape = RoundedCornerShape(999.dp),
      )
    }
  }
}

@Composable
private fun AttachmentRow(file: PostFile, cdnUrl: String) {
  val displayName = file.name ?: file.path?.substringAfterLast('/') ?: "attachment"
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.DownloadW400Outlined,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.width(10.dp))
    Text(
        text = displayName,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun CommentRow(comment: Comment, onParentClick: (() -> Unit)?) {
  Surface(
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
  ) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = comment.commenterName ?: comment.commenter ?: "Anonymous",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        if (!comment.published.isNullOrBlank()) {
          Spacer(Modifier.width(8.dp))
          Text(
              text = comment.published.take(10),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      val parentId = comment.parentId?.takeIf { it.isNotBlank() }
      if (parentId != null) {
        Text(
            text = ">>$parentId",
            style = MaterialTheme.typography.labelSmall,
            color =
                if (onParentClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(top = 3.dp, bottom = 3.dp)
                    .then(
                        if (onParentClick != null) Modifier.clickable(onClick = onParentClick)
                        else Modifier
                    ),
        )
      }
      if (comment.content.isNotBlank()) {
        val html = remember(comment.content) { htmlToAnnotatedString(comment.content) }
        Text(
            text = html,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

// 加载中显示 1:1 骨架屏；加载成功后切换为图片真实 aspect ratio。
// aspectRatioCache 由调用方持有（page 级别），避免 item 出屏销毁 remember 后滚回来时跳动。
@Composable
private fun PostAttachmentImage(
    thumbnailUrl: String?,
    fullUrl: String?,
    contentDescription: String?,
    aspectRatioCache: MutableMap<String, Float>,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    onClick: () -> Unit,
) {
  val url = thumbnailUrl ?: fullUrl ?: return
  val aspectRatio = aspectRatioCache[url]
  SubcomposeAsyncImage(
      model = url,
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier =
          modifier
              .fillMaxWidth()
              .aspectRatio(aspectRatio ?: 1f)
              .clip(shape)
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

@Composable
private fun EmbedRow(url: String, subject: String?, description: String?) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.Top,
  ) {
    Icon(
        imageVector = Icons.LinkW400Outlined,
        contentDescription = null,
        modifier = Modifier.size(18.dp).padding(top = 2.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.width(8.dp))
    Column {
      Text(
          text = subject?.takeIf { it.isNotBlank() } ?: url,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.primary,
      )
      if (!description.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
