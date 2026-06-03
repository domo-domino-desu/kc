package ddd.kc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

@Composable
fun NetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    thumbnailUrl: String? = null,
) {
  val fallbackUrl = thumbnailUrl?.takeIf { it.isNotBlank() && it != url }
  if (url.isNullOrBlank()) {
    if (fallbackUrl.isNullOrBlank()) {
      Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
      NetworkImage(
          url = fallbackUrl,
          modifier = modifier,
          contentScale = contentScale,
          contentDescription = contentDescription,
      )
    }
    return
  }
  SubcomposeAsyncImage(
      model = url,
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = modifier,
      loading = {
        if (!fallbackUrl.isNullOrBlank()) {
          NetworkImage(
              url = fallbackUrl,
              modifier = Modifier.fillMaxSize(),
              contentScale = contentScale,
              contentDescription = contentDescription,
          )
        } else {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
      },
      error = {
        if (!fallbackUrl.isNullOrBlank()) {
          NetworkImage(
              url = fallbackUrl,
              modifier = Modifier.fillMaxSize(),
              contentScale = contentScale,
              contentDescription = contentDescription,
          )
        } else {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
      },
      success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
  )
}
