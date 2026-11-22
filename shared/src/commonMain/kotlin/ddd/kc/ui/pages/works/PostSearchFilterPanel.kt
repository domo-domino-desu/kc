package ddd.kc.ui.pages.works

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ddd.kc.data.model.AiFilterMode
import ddd.kc.data.model.PostSearchFilterError
import ddd.kc.data.model.PostSearchFilters
import ddd.kc.data.model.PostSearchMedia
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ExpandMoreW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FilterAltW400Outlined
import ddd.kc.ui.components.aiFilterLabel
import ddd.kc.ui.components.forms.FilterField
import ddd.kc.ui.components.forms.FilterOption
import ddd.kc.ui.components.forms.FilterPanel
import ddd.kc.ui.components.icons.ServiceIconCatalogRepository
import ddd.kc.ui.components.icons.pawchiveServices
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.ai_filter_label
import kc.shared.generated.resources.filter_title
import kc.shared.generated.resources.search_filter_after
import kc.shared.generated.resources.search_filter_all
import kc.shared.generated.resources.search_filter_archive
import kc.shared.generated.resources.search_filter_audio
import kc.shared.generated.resources.search_filter_before
import kc.shared.generated.resources.search_filter_date_hint
import kc.shared.generated.resources.search_filter_image
import kc.shared.generated.resources.search_filter_invalid_date
import kc.shared.generated.resources.search_filter_invalid_range
import kc.shared.generated.resources.search_filter_media
import kc.shared.generated.resources.search_filter_scope
import kc.shared.generated.resources.search_filter_service
import kc.shared.generated.resources.search_filter_title_content
import kc.shared.generated.resources.search_filter_title_only
import kc.shared.generated.resources.search_filter_video
import kc.shared.generated.resources.search_syntax_details
import kc.shared.generated.resources.search_syntax_help
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PostSearchFilterPanel(
    filters: PostSearchFilters,
    aiFilter: AiFilterMode,
    onFiltersChanged: (PostSearchFilters) -> Unit,
    onAiFilterChanged: (AiFilterMode) -> Unit,
    onSubmit: () -> Unit,
) {
  LaunchedEffect(Unit) { ServiceIconCatalogRepository.ensureLoaded() }
  val catalog by ServiceIconCatalogRepository.catalog.collectAsState()
  val services =
      catalog?.servicesForPlatform("pawchive")?.takeIf { it.isNotEmpty() } ?: pawchiveServices()
  val all = stringResource(Res.string.search_filter_all)
  val fields =
      listOf(
          FilterField.Dropdown(
              "ai",
              stringResource(Res.string.ai_filter_label),
              aiFilter,
              AiFilterMode.entries.map { FilterOption(it, aiFilterLabel(it)) },
              onAiFilterChanged,
          ),
          FilterField.Dropdown(
              "scope",
              stringResource(Res.string.search_filter_scope),
              filters.titleOnly,
              listOf(
                  FilterOption(false, stringResource(Res.string.search_filter_title_content)),
                  FilterOption(true, stringResource(Res.string.search_filter_title_only)),
              ),
              { onFiltersChanged(filters.copy(titleOnly = it)) },
          ),
          FilterField.Input(
              "after",
              stringResource(Res.string.search_filter_after),
              filters.after,
              { onFiltersChanged(filters.copy(after = it)) },
              all,
              stringResource(Res.string.search_filter_date_hint),
              onSubmit = onSubmit,
              submitEnabled = filters.error == null,
          ),
          FilterField.Input(
              "before",
              stringResource(Res.string.search_filter_before),
              filters.before,
              { onFiltersChanged(filters.copy(before = it)) },
              all,
              stringResource(Res.string.search_filter_date_hint),
              onSubmit = onSubmit,
              submitEnabled = filters.error == null,
          ),
          FilterField.Dropdown(
              "service",
              stringResource(Res.string.search_filter_service),
              filters.service,
              listOf(FilterOption("", all)) +
                  services.map { FilterOption(it, catalog?.services?.get(it)?.label ?: it) },
              { onFiltersChanged(filters.copy(service = it)) },
          ),
          FilterField.Dropdown(
              "media",
              stringResource(Res.string.search_filter_media),
              filters.media,
              PostSearchMedia.entries.map { media ->
                FilterOption(
                    media,
                    when (media) {
                      PostSearchMedia.ANY -> all
                      PostSearchMedia.IMAGE -> stringResource(Res.string.search_filter_image)
                      PostSearchMedia.VIDEO -> stringResource(Res.string.search_filter_video)
                      PostSearchMedia.AUDIO -> stringResource(Res.string.search_filter_audio)
                      PostSearchMedia.ARCHIVE -> stringResource(Res.string.search_filter_archive)
                    },
                )
              },
              { onFiltersChanged(filters.copy(media = it)) },
          ),
      )
  Column(Modifier.fillMaxWidth()) {
    FilterPanel(
        fields,
        Icons.FilterAltW400Outlined,
        Icons.ExpandMoreW400Outlined,
        stringResource(Res.string.filter_title),
    )
    val error = filters.error
    if (error != null)
        Text(
            stringResource(
                if (error == PostSearchFilterError.INVALID_DATE)
                    Res.string.search_filter_invalid_date
                else Res.string.search_filter_invalid_range
            ),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    var showHelp by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showHelp = !showHelp }) {
      Text(stringResource(Res.string.search_syntax_help))
    }
    if (showHelp)
        Text(
            stringResource(Res.string.search_syntax_details),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
  }
}
