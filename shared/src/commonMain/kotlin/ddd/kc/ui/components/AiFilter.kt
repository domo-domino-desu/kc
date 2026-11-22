package ddd.kc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ddd.kc.data.model.AiFilterMode
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ExpandLessW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FilterAltW400Outlined
import ddd.kc.ui.components.forms.FilterField
import ddd.kc.ui.components.forms.FilterOption
import ddd.kc.ui.components.forms.FilterPanel
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.ai_filter_label
import kc.shared.generated.resources.filter_collapse
import kc.shared.generated.resources.filter_hide
import kc.shared.generated.resources.filter_only
import kc.shared.generated.resources.filter_show
import kc.shared.generated.resources.filter_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun aiFilterField(
    value: AiFilterMode,
    onChange: (AiFilterMode) -> Unit,
): FilterField =
    FilterField.Dropdown(
        key = "ai",
        label = stringResource(Res.string.ai_filter_label),
        value = value,
        options = AiFilterMode.entries.map { FilterOption(it, aiFilterLabel(it)) },
        onChange = onChange,
        fallbackLabel = aiFilterLabel(value),
    )

@Composable
fun aiFilterLabel(value: AiFilterMode): String =
    when (value) {
      AiFilterMode.SHOW -> stringResource(Res.string.filter_show)
      AiFilterMode.HIDE -> stringResource(Res.string.filter_hide)
      AiFilterMode.ONLY -> stringResource(Res.string.filter_only)
    }

@Composable
fun AiFilterPanel(
    value: AiFilterMode,
    onChange: (AiFilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
  FilterPanel(
      fields = listOf(aiFilterField(value, onChange)),
      filterIcon = Icons.FilterAltW400Outlined,
      collapseIcon = Icons.ExpandLessW400Outlined,
      openLabel = stringResource(Res.string.filter_title),
      closeLabel = stringResource(Res.string.filter_collapse),
      modifier = modifier,
  )
}
