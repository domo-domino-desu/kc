package ddd.kc.ui.pages.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import ddd.kc.data.model.Comment
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.DownloadW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.HdW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.HdW400OutlinedFill
import ddd.kc.generated.symbols.icons.materialsymbols.icons.LinkW400Outlined
import ddd.kc.ui.components.ImageLoadLifecycleState
import ddd.kc.ui.components.NetworkImage
import ddd.kc.ui.components.PlatformVideoPlayer
import ddd.kc.ui.components.SkeletonBlock
import ddd.kc.ui.components.TopLinearImageLoadingProgress
import ddd.kc.ui.components.paging.TranslationBlockState
import ddd.kc.ui.components.paging.TranslationStatus
import ddd.kc.ui.components.rememberImageLoadProgressState
import ddd.kc.utils.collapseConsecutiveBlankLines
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.load_full_size_images
import kc.shared.generated.resources.post_not_archived
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TranslatedBlockItem(block: TranslationBlockState, showDivider: Boolean) {
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
internal fun PostInfoMetric(
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
internal fun PostInfoIconMetric(
    icon: ImageVector,
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
  }
}

@Composable
internal fun PostInfoSkeleton() {
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
internal fun PostTagsSkeleton() {
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
internal fun PostAttachmentSkeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
) {
  SkeletonBlock(modifier = modifier.fillMaxWidth().aspectRatio(1f).clip(shape))
}

@Composable
internal fun AttachmentRow(file: PostFile, isDownloading: Boolean, onDownload: () -> Unit) {
  val displayName = file.name ?: file.path?.substringAfterLast('/') ?: "attachment"
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .then(if (!isDownloading) Modifier.clickable(onClick = onDownload) else Modifier)
              .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isDownloading) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
      Icon(
          imageVector = Icons.DownloadW400Outlined,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
      )
    }
    Spacer(Modifier.width(10.dp))
    Text(
        text = displayName,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f),
    )
  }
}

@Composable
internal fun PostAttachmentVideo(
    file: PostFile,
    url: String?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier,
    shape: RoundedCornerShape,
    border: BorderStroke,
) {
  Surface(shape = shape, border = border, modifier = modifier.fillMaxWidth()) {
    Column {
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .aspectRatio(16f / 9f)
                  .background(androidx.compose.ui.graphics.Color.Black),
          contentAlignment = Alignment.Center,
      ) {
        if (!url.isNullOrBlank()) {
          PlatformVideoPlayer(url = url, modifier = Modifier.fillMaxSize())
        } else {
          Text(
              text = file.name ?: "video",
              style = MaterialTheme.typography.bodySmall,
              color = androidx.compose.ui.graphics.Color.White,
          )
        }
      }
      AttachmentRow(file = file, isDownloading = isDownloading, onDownload = onDownload)
    }
  }
}

internal fun PostFile.downloadFileName(): String =
    (name ?: path?.substringAfterLast('/') ?: "attachment").sanitizeFileName().ifBlank {
      "attachment"
    }

internal fun String.sanitizeFileName(): String =
    trim()
        .map { char ->
          when {
            char == '/' || char == '\\' -> '_'
            char.code < 32 -> '_'
            else -> char
          }
        }
        .joinToString("")
        .take(180)

internal fun String.guessMimeType(): String =
    when (substringAfterLast('.', "").lowercase()) {
      "jpg",
      "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "gif" -> "image/gif"
      "webp" -> "image/webp"
      "avif" -> "image/avif"
      "bmp" -> "image/bmp"
      "mp4",
      "m4v" -> "video/mp4"
      "webm" -> "video/webm"
      "mov" -> "video/quicktime"
      "mp3" -> "audio/mpeg"
      "m4a" -> "audio/mp4"
      "wav" -> "audio/wav"
      "ogg" -> "audio/ogg"
      "flac" -> "audio/flac"
      "zip" -> "application/zip"
      "rar" -> "application/vnd.rar"
      "7z" -> "application/x-7z-compressed"
      "pdf" -> "application/pdf"
      "txt" -> "text/plain"
      else -> "application/octet-stream"
    }

@Composable
internal fun CommentRow(comment: Comment, onParentClick: (() -> Unit)?) {
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
        val html =
            remember(comment.content) {
              htmlToAnnotatedString(collapseConsecutiveBlankLines(comment.content))
            }
        Text(
            text = html,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

@Composable
internal fun PostNotArchivedNotice(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke,
) {
  Surface(
      shape = shape,
      border = border,
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
      modifier = modifier.fillMaxWidth(),
  ) {
    Text(
        text = stringResource(Res.string.post_not_archived),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
  }
}

@Composable
internal fun LoadFullSizeImagesIconButton(
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.size(32.dp),
  ) {
    Icon(
        imageVector = if (isActive) Icons.HdW400OutlinedFill else Icons.HdW400Outlined,
        contentDescription = stringResource(Res.string.load_full_size_images),
        modifier = Modifier.size(18.dp),
        tint =
            when {
              isActive -> MaterialTheme.colorScheme.primary
              enabled -> MaterialTheme.colorScheme.onSurfaceVariant
              else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
    )
  }
}

internal class PostAttachmentImageState(
    showFullImage: Boolean,
    fullImageLoaded: Boolean,
    loadLifecycleState: ImageLoadLifecycleState = ImageLoadLifecycleState.Idle,
) {
  var showFullImage by mutableStateOf(showFullImage)
  var fullImageLoaded by mutableStateOf(fullImageLoaded)
  var loadLifecycleState by mutableStateOf(loadLifecycleState)
}

internal fun createPostAttachmentImageState(
    thumbnailUrl: String?,
    fullUrl: String?,
): PostAttachmentImageState {
  val hasDistinctFullUrl = !fullUrl.isNullOrBlank() && fullUrl != thumbnailUrl
  return PostAttachmentImageState(
      showFullImage = !hasDistinctFullUrl,
      fullImageLoaded = !hasDistinctFullUrl,
  )
}

// 加载中显示 1:1 骨架屏；加载成功后切换为图片真实 aspect ratio。
// aspectRatioCache 由调用方持有（page 级别），避免 item 出屏销毁 remember 后滚回来时跳动。
@Composable
internal fun PostAttachmentImage(
    thumbnailUrl: String?,
    fullUrl: String?,
    contentDescription: String?,
    aspectRatioCache: MutableMap<String, Float>,
    imageState: PostAttachmentImageState,
    fullImageEnabled: Boolean,
    loadFullSizeImage: Boolean,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    onFullSizeImageRequested: () -> Unit,
    onClick: () -> Unit,
) {
  val hasDistinctFullUrl = !fullUrl.isNullOrBlank() && fullUrl != thumbnailUrl
  LaunchedEffect(loadFullSizeImage, hasDistinctFullUrl, fullImageEnabled) {
    if (fullImageEnabled && loadFullSizeImage && hasDistinctFullUrl) {
      imageState.showFullImage = true
    }
  }
  val url = if (imageState.showFullImage) fullUrl ?: thumbnailUrl else thumbnailUrl ?: fullUrl
  if (url.isNullOrBlank()) return
  val progressState =
      rememberImageLoadProgressState(
          progressKey = if (url == fullUrl) fullUrl else null,
          lifecycleState = imageState.loadLifecycleState,
      )
  val aspectRatio = aspectRatioCache[url] ?: thumbnailUrl?.let { aspectRatioCache[it] }
  val clickAction = {
    if (!fullImageEnabled) {
      Unit
    } else if (hasDistinctFullUrl && !loadFullSizeImage && !imageState.fullImageLoaded) {
      onFullSizeImageRequested()
    } else {
      onClick()
    }
  }
  SubcomposeAsyncImage(
      model = url,
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier =
          modifier
              .fillMaxWidth()
              .aspectRatio(aspectRatio ?: 1f)
              .clip(shape)
              .then(if (fullImageEnabled) Modifier.clickable(onClick = clickAction) else Modifier),
      onLoading = { imageState.loadLifecycleState = ImageLoadLifecycleState.Loading },
      onSuccess = { state ->
        imageState.loadLifecycleState = ImageLoadLifecycleState.Success
        val size = state.painter.intrinsicSize
        if (
            size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()
        ) {
          aspectRatioCache[url] = size.width / size.height
        }
        imageState.fullImageLoaded = !hasDistinctFullUrl || url == fullUrl
      },
      onError = {
        imageState.loadLifecycleState = ImageLoadLifecycleState.Error
        if (url == fullUrl && hasDistinctFullUrl) {
          imageState.showFullImage = false
          imageState.fullImageLoaded = false
        }
      },
      loading = {
        if (url == fullUrl && !thumbnailUrl.isNullOrBlank()) {
          Box(modifier = Modifier.fillMaxSize()) {
            NetworkImage(
                url = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                contentDescription = contentDescription,
            )
            TopLinearImageLoadingProgress(
                progressState = progressState,
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
          }
        } else {
          SkeletonBlock(modifier = Modifier.fillMaxSize())
        }
      },
      error = {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
      },
      success = { SubcomposeAsyncImageContent() },
  )
}

@Composable
internal fun EmbedRow(url: String, subject: String?, description: String?) {
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
