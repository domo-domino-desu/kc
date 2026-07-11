package ddd.kc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ArrowBackW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.HomeW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.TranslateW400Outlined
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.back
import kc.shared.generated.resources.home
import kc.shared.generated.resources.translate_action
import org.jetbrains.compose.resources.stringResource

/** Simple top bar with only a back button. Used for tag posts and similar secondary pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackAppBar(title: String = "") {
  val navigator = LocalNavigator.currentOrThrow
  TopAppBar(
      title = { if (title.isNotEmpty()) Text(title) },
      navigationIcon = {
        IconButton(onClick = { navigator.pop() }) {
          Icon(
              imageVector = Icons.ArrowBackW400Outlined,
              contentDescription = stringResource(Res.string.back),
          )
        }
      },
  )
}

/**
 * Top bar for detail screens (Post, Creator, ImageViewer). NavigationIcon row: ← Back | ⌂ Home
 * (popUntilRoot) Title area: scroll-to-top (optional). Actions: Share (optional) | extra actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailAppBar(
    title: String = "",
    shareUrl: String? = null,
    onTranslate: (() -> Unit)? = null,
    isTranslating: Boolean = false,
    isTranslateActive: Boolean = false,
    onScrollToTop: (() -> Unit)? = null,
    leadingActions: @Composable () -> Unit = {},
    extraActions: @Composable () -> Unit = {},
) {
  val navigator = LocalNavigator.currentOrThrow
  TopAppBar(
      title = {
        Box(
            modifier =
                Modifier.fillMaxWidth().height(48.dp).clickable(enabled = onScrollToTop != null) {
                  onScrollToTop?.invoke()
                },
            contentAlignment = Alignment.CenterStart,
        ) {
          if (title.isNotEmpty()) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
        }
      },
      navigationIcon = {
        Row {
          IconButton(onClick = { navigator.pop() }) {
            Icon(
                imageVector = Icons.ArrowBackW400Outlined,
                contentDescription = stringResource(Res.string.back),
            )
          }
          IconButton(onClick = { navigator.popUntilRoot() }) {
            Icon(
                imageVector = Icons.HomeW400Outlined,
                contentDescription = stringResource(Res.string.home),
            )
          }
        }
      },
      actions = {
        leadingActions()
        if (onTranslate != null) {
          TranslateIconButton(
              onClick = onTranslate,
              isTranslating = isTranslating,
              isActive = isTranslateActive,
          )
        }
        if (!shareUrl.isNullOrBlank()) {
          ShareIconButton(url = shareUrl)
        }
        extraActions()
      },
  )
}

@Composable
fun TranslateIconButton(
    onClick: () -> Unit,
    isTranslating: Boolean,
    isActive: Boolean,
) {
  IconButton(
      onClick = onClick,
      enabled = !isTranslating || isActive,
      modifier = Modifier.size(32.dp),
  ) {
    if (isTranslating) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    } else {
      Icon(
          imageVector = Icons.TranslateW400Outlined,
          contentDescription = stringResource(Res.string.translate_action),
          modifier = Modifier.size(18.dp),
          tint =
              if (isActive) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
