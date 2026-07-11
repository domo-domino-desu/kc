package ddd.kc.ui.pages.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.QueryError
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.canLoadFullImage
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.imageFiles
import ddd.kc.data.model.isImage
import ddd.kc.data.model.isVideo
import ddd.kc.data.model.key
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.AttachFileW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CalendarAddOnW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.CommentW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.DateRangeW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlinedfill1
import ddd.kc.generated.symbols.icons.materialsymbols.icons.TagW400Outlined
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.i18n.localizedMessage
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.utils.collapseConsecutiveBlankLines
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.comments_count
import kc.shared.generated.resources.no_comments
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val PostTitleItemKey = "post-title"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PostDetailPage(
    post: Post,
    cdnUrl: String,
    baseUrl: String,
    listState: LazyListState,
    isFavorite: Boolean,
    favoriteError: QueryError?,
    isDetailLoading: Boolean,
    creator: Creator?,
    comments: List<Comment>,
    commentError: QueryError?,
    translationState: ContentTranslationState,
    requestedFullImageUrls: Set<String>,
    onFavoriteClick: () -> Unit,
    onTranslate: () -> Unit,
    onFullImageRequested: (String) -> Unit,
    onFullImagesRequested: (Collection<String>) -> Unit,
    onTagClick: (String) -> Unit,
    onArtistClick: () -> Unit,
    onScrollToTop: () -> Unit,
    onImageClick: (Int) -> Unit,
    isAttachmentDownloading: (PostFile) -> Boolean,
    onAttachmentDownload: (PostFile) -> Unit,
) {
  val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
  val cardShape = RoundedCornerShape(14.dp)
  val scope = rememberCoroutineScope()
  val postTitle = post.title.orEmpty()
  val showTitleInAppBar by
      remember(post.key, postTitle, listState) {
        derivedStateOf {
          postTitle.isNotBlank() &&
              listState.layoutInfo.totalItemsCount > 0 &&
              listState.layoutInfo.visibleItemsInfo.none { it.key == PostTitleItemKey }
        }
      }
  // page 级别缓存，key=url，避免 item 出屏后状态丢失导致滚动跳动
  val imageAspectRatios = remember(post.key) { mutableStateMapOf<String, Float>() }
  val imageLoadStates = remember(post.key) { mutableStateMapOf<String, PostAttachmentImageState>() }
  val imageFiles = remember(post) { post.imageFiles() }
  val loadableFullImageUrls =
      remember(post, imageFiles, cdnUrl) {
        imageFiles.mapNotNull { file ->
          if (!file.canLoadFullImage(post)) return@mapNotNull null
          val thumbnailUrl = file.thumbnailUrl(cdnUrl)
          val fullUrl = file.fullUrl(cdnUrl)
          fullUrl?.takeIf { it.isNotBlank() && it != thumbnailUrl }
        }
      }
  val hasImages = imageFiles.isNotEmpty()
  val hasLoadableFullImages = remember(loadableFullImageUrls) { loadableFullImageUrls.isNotEmpty() }
  val allFullImagesRequested =
      hasLoadableFullImages && requestedFullImageUrls.containsAll(loadableFullImageUrls)
  val showPostNotArchivedPrompt = hasImages && !hasLoadableFullImages
  ErrorToastEffect(favoriteError?.localizedMessage())
  ErrorToastEffect(commentError?.localizedMessage())

  Scaffold(
      topBar = {
        val shareUrl = "$baseUrl/${post.service}/user/${post.creatorId}/post/${post.id}"
        DetailAppBar(
            title = if (showTitleInAppBar) postTitle else "",
            shareUrl = shareUrl,
            onTranslate = if (!post.content.isNullOrBlank()) onTranslate else null,
            isTranslating = translationState.isTranslating,
            isTranslateActive = translationState.showTranslation,
            onScrollToTop = onScrollToTop,
            leadingActions = {
              if (hasImages) {
                LoadFullSizeImagesIconButton(
                    isActive = allFullImagesRequested,
                    enabled = hasLoadableFullImages && !allFullImagesRequested,
                    onClick = { onFullImagesRequested(loadableFullImageUrls) },
                )
              }
            },
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
    val videoFiles = remember(post) { post.allFiles().filter { it.isVideo() } }
    val otherFiles = remember(post) { post.allFiles().filter { !it.isImage() && !it.isVideo() } }
    val knownAttachmentCount = post.attachmentCount ?: post.allFiles().size
    val knownRenderedAttachmentCount = imageFiles.size + videoFiles.size + otherFiles.size
    val loadingAttachmentSkeletonCount =
        if (isDetailLoading) {
          (knownAttachmentCount - knownRenderedAttachmentCount).coerceAtLeast(0)
        } else {
          0
        }
    val commentsItemIndex =
        remember(
            post.title,
            post.tags,
            post.content,
            imageFiles.size,
            otherFiles.size,
            videoFiles.size,
            loadingAttachmentSkeletonCount,
            post.embed,
        ) {
          var index = 0
          if (!post.title.isNullOrBlank()) index += 1
          index += 1 // author
          index += 1 // metadata
          index += 1 // tags / skeleton placeholder
          if (!post.content.isNullOrBlank()) index += 1
          index += imageFiles.size
          index += videoFiles.size
          index += loadingAttachmentSkeletonCount
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
      if (postTitle.isNotBlank()) {
        item(key = PostTitleItemKey) {
          Text(
              text = postTitle,
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
                url = creator.thumbnailUrl(baseUrl),
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
              itemVerticalAlignment = Alignment.CenterVertically,
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
            if (post.allFiles().isNotEmpty()) {
              PostInfoMetric(
                  icon = Icons.AttachFileW400Outlined,
                  text = post.allFiles().size.toString(),
              )
            }
            PostInfoIconMetric(
                icon = Icons.CommentW400Outlined,
                onClick = { scope.launch { listState.animateScrollToItem(commentsItemIndex) } },
            )
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
            val htmlText =
                remember(post.content) {
                  htmlToAnnotatedString(collapseConsecutiveBlankLines(post.content))
                }
            Text(
                text = htmlText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
          }
        }
      }

      // ── Images ────────────────────────────────────────────────────
      if (showPostNotArchivedPrompt) {
        item {
          PostNotArchivedNotice(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              shape = cardShape,
              border = cardBorder,
          )
        }
      }

      itemsIndexed(imageFiles, key = { idx, _ -> "img-$idx" }) { idx, file ->
        val thumbnailUrl = file.thumbnailUrl(cdnUrl)
        val fullUrl = file.fullUrl(cdnUrl)
        val fullImageEnabled = file.canLoadFullImage(post)
        val imageStateKey = fullUrl ?: thumbnailUrl ?: "img-$idx"
        val imageState =
            remember(imageStateKey) {
              imageLoadStates.getOrPut(imageStateKey) {
                createPostAttachmentImageState(thumbnailUrl = thumbnailUrl, fullUrl = fullUrl)
              }
            }
        PostAttachmentImage(
            thumbnailUrl = thumbnailUrl,
            fullUrl = fullUrl,
            contentDescription = file.name,
            aspectRatioCache = imageAspectRatios,
            imageState = imageState,
            fullImageEnabled = fullImageEnabled,
            loadFullSizeImage =
                fullImageEnabled && !fullUrl.isNullOrBlank() && fullUrl in requestedFullImageUrls,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            shape = cardShape,
            onFullSizeImageRequested = {
              if (fullImageEnabled && !fullUrl.isNullOrBlank()) {
                onFullImageRequested(fullUrl)
              }
            },
            onClick = { onImageClick(idx) },
        )
      }

      if (loadingAttachmentSkeletonCount > 0) {
        itemsIndexed(
            List(loadingAttachmentSkeletonCount) { it },
            key = { index, _ -> "attachment-loading-$index" },
        ) { _, _ ->
          PostAttachmentSkeleton(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
              shape = cardShape,
          )
        }
      }

      // ── Videos ───────────────────────────────────────────────────
      itemsIndexed(videoFiles, key = { idx, _ -> "video-$idx" }) { _, file ->
        PostAttachmentVideo(
            file = file,
            url = file.fullUrl(cdnUrl),
            isDownloading = isAttachmentDownloading(file),
            onDownload = { onAttachmentDownload(file) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            shape = cardShape,
            border = cardBorder,
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
              otherFiles.forEach { file ->
                AttachmentRow(
                    file = file,
                    isDownloading = isAttachmentDownloading(file),
                    onDownload = { onAttachmentDownload(file) },
                )
              }
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
