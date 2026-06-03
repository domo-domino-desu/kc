package ddd.kc.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ShareW400Outlined
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.copy_link
import kc.shared.generated.resources.link_copied
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShareIconButton(url: String) {
  val clipboard = LocalClipboardManager.current
  val showToast = LocalShowToast.current
  val linkCopied = stringResource(Res.string.link_copied)
  IconButton(
      onClick = {
        clipboard.setText(AnnotatedString(url))
        showToast(linkCopied)
      }
  ) {
    Icon(
        imageVector = Icons.ShareW400Outlined,
        contentDescription = stringResource(Res.string.copy_link),
    )
  }
}
