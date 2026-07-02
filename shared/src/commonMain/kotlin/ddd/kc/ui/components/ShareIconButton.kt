package ddd.kc.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ShareW400Outlined
import ddd.kc.ui.components.platform.rememberPlatformTextCopier
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.copy_link
import kc.shared.generated.resources.link_copied
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShareIconButton(url: String) {
  val copyText = rememberPlatformTextCopier()
  val showToast = LocalShowToast.current
  val linkCopied = stringResource(Res.string.link_copied)
  IconButton(onClick = { if (copyText(url)) showToast(linkCopied) }) {
    Icon(
        imageVector = Icons.ShareW400Outlined,
        contentDescription = stringResource(Res.string.copy_link),
    )
  }
}
