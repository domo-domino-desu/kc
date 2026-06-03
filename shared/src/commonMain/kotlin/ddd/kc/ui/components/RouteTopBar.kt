package ddd.kc.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ArrowBackW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.HomeW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.TranslateW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.VerticalAlignTopW400Outlined

/** Simple top bar with only a back button. Used for tag posts and similar secondary pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackAppBar(title: String = "") {
  val navigator = LocalNavigator.currentOrThrow
  TopAppBar(
      title = { if (title.isNotEmpty()) Text(title) },
      navigationIcon = {
        IconButton(onClick = { navigator.pop() }) {
          Icon(imageVector = Icons.ArrowBackW400Outlined, contentDescription = "返回")
        }
      },
  )
}

/**
 * Top bar for detail screens (Post, Creator, ImageViewer). NavigationIcon row: ← Back | ⌂ Home
 * (popUntilRoot) Actions: ↑ Scroll-to-top (optional) | Share (optional) | extra actions
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
    extraActions: @Composable () -> Unit = {},
) {
  val navigator = LocalNavigator.currentOrThrow
  TopAppBar(
      title = { if (title.isNotEmpty()) Text(title) },
      navigationIcon = {
        Row {
          IconButton(onClick = { navigator.pop() }) {
            Icon(imageVector = Icons.ArrowBackW400Outlined, contentDescription = "返回")
          }
          IconButton(onClick = { navigator.popUntilRoot() }) {
            Icon(imageVector = Icons.HomeW400Outlined, contentDescription = "主页")
          }
        }
      },
      actions = {
        if (onTranslate != null) {
          TranslateIconButton(
              onClick = onTranslate,
              isTranslating = isTranslating,
              isActive = isTranslateActive,
          )
        }
        if (onScrollToTop != null) {
          IconButton(onClick = onScrollToTop) {
            Icon(imageVector = Icons.VerticalAlignTopW400Outlined, contentDescription = "回顶")
          }
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
          contentDescription = "翻译",
          modifier = Modifier.size(18.dp),
          tint =
              if (isActive) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
