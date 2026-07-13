package ddd.kc.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.local.settings.DownloadFileNameMode
import ddd.kc.data.model.TranslationProvider
import ddd.kc.data.model.TranslationTargetLanguage
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ArrowBackW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.HomeW400Outlined
import ddd.kc.ui.components.NavigationBackHandler
import ddd.kc.ui.components.platform.rememberPlatformDirectoryPicker
import ddd.kc.ui.pages.settings.components.SettingsDropdownField
import ddd.kc.ui.pages.settings.components.SettingsGroup
import ddd.kc.ui.pages.settings.components.SettingsInputRow
import ddd.kc.ui.pages.settings.components.SettingsNavigationRow
import ddd.kc.ui.pages.settings.components.SettingsStackedInputRow
import ddd.kc.ui.pages.settings.components.SettingsSwitchRow
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.api_key
import kc.shared.generated.resources.app_language
import kc.shared.generated.resources.app_language_desc
import kc.shared.generated.resources.appearance
import kc.shared.generated.resources.base_url
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.card_width
import kc.shared.generated.resources.chunk_word_limit
import kc.shared.generated.resources.close_without_saving
import kc.shared.generated.resources.custom
import kc.shared.generated.resources.download_allow_media_indexing
import kc.shared.generated.resources.download_allow_media_indexing_desc
import kc.shared.generated.resources.download_file_name_mode
import kc.shared.generated.resources.download_file_name_mode_desc
import kc.shared.generated.resources.download_file_name_template
import kc.shared.generated.resources.download_file_name_template_desc
import kc.shared.generated.resources.download_save_path
import kc.shared.generated.resources.download_save_path_not_configured
import kc.shared.generated.resources.download_settings
import kc.shared.generated.resources.download_subfolder_mode
import kc.shared.generated.resources.download_subfolder_mode_desc
import kc.shared.generated.resources.enable_translation
import kc.shared.generated.resources.enable_translation_desc
import kc.shared.generated.resources.hide_api_key
import kc.shared.generated.resources.max_concurrency
import kc.shared.generated.resources.model
import kc.shared.generated.resources.openai_api_key_desc
import kc.shared.generated.resources.openai_base_url_desc
import kc.shared.generated.resources.openai_model_desc
import kc.shared.generated.resources.openai_prompt_template_desc
import kc.shared.generated.resources.pawchive_base_url
import kc.shared.generated.resources.pawchive_base_url_desc
import kc.shared.generated.resources.pawchive_settings
import kc.shared.generated.resources.prompt_template
import kc.shared.generated.resources.provider
import kc.shared.generated.resources.reset
import kc.shared.generated.resources.save
import kc.shared.generated.resources.settings
import kc.shared.generated.resources.settings_discard_changes
import kc.shared.generated.resources.settings_save_and_exit
import kc.shared.generated.resources.settings_save_failed
import kc.shared.generated.resources.settings_saved
import kc.shared.generated.resources.settings_unsaved_changes_body
import kc.shared.generated.resources.settings_unsaved_changes_title
import kc.shared.generated.resources.show_api_key
import kc.shared.generated.resources.theme_mode
import kc.shared.generated.resources.theme_mode_desc
import kc.shared.generated.resources.translation
import kc.shared.generated.resources.translation_provider_desc
import kc.shared.generated.resources.translation_target_language
import kc.shared.generated.resources.translation_target_language_desc
import org.jetbrains.compose.resources.stringResource

private enum class PendingExitAction {
  Back,
  Home,
}

private enum class PawchiveBaseUrlOption {
  St,
  Pw,
  Custom,
}

private val PawchiveBaseUrlOption.fixedBaseUrl: String?
  get() =
      when (this) {
        PawchiveBaseUrlOption.St -> "https://pawchive.st"
        PawchiveBaseUrlOption.Pw -> AppSettings.PAWCHIVE_ALT_BASE_URL
        PawchiveBaseUrlOption.Custom -> null
      }

private fun pawchiveBaseUrlOptionFor(baseUrl: String): PawchiveBaseUrlOption {
  val normalized = baseUrl.trim().trimEnd('/')
  return when (normalized) {
    "https://pawchive.st" -> PawchiveBaseUrlOption.St
    AppSettings.PAWCHIVE_ALT_BASE_URL -> PawchiveBaseUrlOption.Pw
    else -> PawchiveBaseUrlOption.Custom
  }
}

@Composable
private fun pawchiveBaseUrlOptionLabel(option: PawchiveBaseUrlOption): String =
    option.fixedBaseUrl ?: stringResource(Res.string.custom)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    appSettings: AppSettings,
    screenModel: SettingsScreenModel,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
  val listState = rememberLazyListState()
  val saveState by screenModel.state.collectAsState()

  val cardWidth by appSettings.cellMinWidthDpFlow().collectAsState(appSettings.cellMinWidthDp())
  val pawchiveBaseUrl by appSettings.baseUrlFlow().collectAsState(appSettings.baseUrl())
  val language by appSettings.languageFlow().collectAsState(appSettings.language())
  val themeMode by appSettings.themeModeFlow().collectAsState(appSettings.themeMode())
  val translationSettings by
      appSettings.translationSettingsFlow().collectAsState(appSettings.translationSettings())
  val downloadSavePath by
      appSettings.downloadSavePathFlow().collectAsState(appSettings.downloadSavePath())
  val allowMediaIndexing by
      appSettings
          .downloadAllowMediaIndexingFlow()
          .collectAsState(appSettings.downloadAllowMediaIndexing())
  val subfolderMode by
      appSettings.downloadSubfolderModeFlow().collectAsState(appSettings.downloadSubfolderMode())
  val fileNameMode by
      appSettings.downloadFileNameModeFlow().collectAsState(appSettings.downloadFileNameMode())
  val customTemplate by
      appSettings
          .downloadCustomFileNameTemplateFlow()
          .collectAsState(appSettings.downloadCustomFileNameTemplate())

  val persisted =
      remember(
          cardWidth,
          pawchiveBaseUrl,
          language,
          themeMode,
          translationSettings,
          downloadSavePath,
          allowMediaIndexing,
          subfolderMode,
          fileNameMode,
          customTemplate,
      ) {
        SettingsDraft(
            themeMode = themeMode,
            language = language,
            cardWidthInput = cardWidth.toString(),
            pawchiveBaseUrl = pawchiveBaseUrl,
            translationEnabled = translationSettings.enabled,
            translationProvider = translationSettings.provider,
            translationTargetLanguage =
                TranslationTargetLanguage.fromPersistedValue(
                    translationSettings.targetLanguageCode
                ),
            chunkWordLimitInput = translationSettings.chunkWordLimit.toString(),
            maxConcurrencyInput = translationSettings.maxConcurrency.toString(),
            openAiBaseUrl = translationSettings.openAiConfig.baseUrl,
            openAiApiKey = translationSettings.openAiConfig.apiKey,
            openAiModel = translationSettings.openAiConfig.model,
            openAiPromptTemplate = translationSettings.openAiConfig.promptTemplate,
            downloadSavePath = downloadSavePath,
            downloadAllowMediaIndexing = allowMediaIndexing,
            downloadSubfolderMode = subfolderMode,
            downloadFileNameMode = fileNameMode,
            downloadCustomFileNameTemplate = customTemplate,
        )
      }

  var draft by remember { mutableStateOf(persisted) }
  var previousPersisted by remember { mutableStateOf(persisted) }
  var initialized by remember { mutableStateOf(false) }
  val saving = saveState.saving
  var showApiKey by remember { mutableStateOf(false) }
  var saveStatusText by remember { mutableStateOf<String?>(null) }
  var pendingExitAction by remember { mutableStateOf<PendingExitAction?>(null) }
  val savedText = stringResource(Res.string.settings_saved)
  val saveFailedText = stringResource(Res.string.settings_save_failed)

  LaunchedEffect(saveState.result) {
    saveStatusText =
        when (saveState.result) {
          SettingsSaveResult.SAVED -> savedText
          SettingsSaveResult.FAILED -> saveFailedText
          null -> null
        }
  }

  LaunchedEffect(persisted) {
    if (!initialized) {
      draft = persisted
      previousPersisted = persisted
      initialized = true
    } else {
      draft = mergePersistedSettingsDraft(draft, previousPersisted, persisted)
      previousPersisted = persisted
    }
  }

  val validationError = draft.validationError()
  val validationMessage = validationError?.localizedMessage()
  val hasUnsavedChanges = initialized && draft != persisted
  val triggerDirectoryPicker = rememberPlatformDirectoryPicker { selectedPath ->
    if (selectedPath != null) draft = draft.copy(downloadSavePath = selectedPath)
  }

  fun executeExit(action: PendingExitAction) {
    when (action) {
      PendingExitAction.Back -> onBack()
      PendingExitAction.Home -> onGoHome()
    }
  }

  fun requestExit(action: PendingExitAction) {
    if (saving) return
    if (!hasUnsavedChanges) executeExit(action) else pendingExitAction = action
  }

  fun saveDraft(onSaved: (() -> Unit)? = null) {
    if (validationMessage != null) {
      saveStatusText = validationMessage
      return
    }
    saveStatusText = null
    screenModel.clearResult()
    screenModel.save(draft, onSaved)
  }

  NavigationBackHandler(enabled = true) { requestExit(PendingExitAction.Back) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.settings)) },
            navigationIcon = {
              Row {
                IconButton(onClick = { requestExit(PendingExitAction.Back) }) {
                  Icon(
                      Icons.ArrowBackW400Outlined,
                      contentDescription = stringResource(Res.string.cancel),
                  )
                }
                IconButton(onClick = { requestExit(PendingExitAction.Home) }) {
                  Icon(
                      Icons.HomeW400Outlined,
                      contentDescription = stringResource(Res.string.close_without_saving),
                  )
                }
              }
            },
            actions = {
              TextButton(onClick = { draft = persisted }, enabled = hasUnsavedChanges && !saving) {
                Text(stringResource(Res.string.reset))
              }
              TextButton(
                  onClick = { saveDraft() },
                  enabled = hasUnsavedChanges && validationMessage == null && !saving,
              ) {
                Text(stringResource(Res.string.save))
              }
            },
        )
      }
  ) { innerPadding ->
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item { AppearanceSettingsSection(draft = draft, onDraftChange = { draft = it }) }
      item { PawchiveSettingsSection(draft = draft, onDraftChange = { draft = it }) }
      item {
        TranslationSettingsSection(
            draft = draft,
            onDraftChange = { draft = it },
            showApiKey = showApiKey,
            onToggleShowApiKey = { showApiKey = !showApiKey },
        )
      }
      item {
        DownloadSettingsSection(
            draft = draft,
            onDraftChange = { draft = it },
            onChooseSavePath = triggerDirectoryPicker,
        )
      }
      validationMessage?.let { message ->
        item {
          Text(
              text = message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(horizontal = 18.dp),
          )
        }
      }
      saveStatusText?.let { message ->
        item {
          Text(
              text = message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 18.dp),
          )
        }
      }
    }
  }

  pendingExitAction?.let { action ->
    AlertDialog(
        onDismissRequest = { pendingExitAction = null },
        title = { Text(stringResource(Res.string.settings_unsaved_changes_title)) },
        text = { Text(stringResource(Res.string.settings_unsaved_changes_body)) },
        confirmButton = {
          TextButton(
              onClick = {
                saveDraft {
                  pendingExitAction = null
                  executeExit(action)
                }
              },
              enabled = validationMessage == null && !saving,
          ) {
            Text(stringResource(Res.string.settings_save_and_exit))
          }
        },
        dismissButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                  pendingExitAction = null
                  executeExit(action)
                },
                enabled = !saving,
            ) {
              Text(stringResource(Res.string.settings_discard_changes))
            }
            TextButton(onClick = { pendingExitAction = null }, enabled = !saving) {
              Text(stringResource(Res.string.cancel))
            }
          }
        },
    )
  }
}

@Composable
private fun PawchiveSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
  val selectedOption = pawchiveBaseUrlOptionFor(draft.pawchiveBaseUrl)
  SettingsGroup(title = stringResource(Res.string.pawchive_settings), framed = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
      SettingsDropdownField(
          label = stringResource(Res.string.pawchive_base_url),
          supportingText = stringResource(Res.string.pawchive_base_url_desc),
          selected = selectedOption,
          options = PawchiveBaseUrlOption.entries,
          optionLabel = { pawchiveBaseUrlOptionLabel(it) },
          onSelect = { option ->
            val nextBaseUrl =
                option.fixedBaseUrl
                    ?: draft.pawchiveBaseUrl
                        .takeUnless {
                          it.trim().trimEnd('/') in AppSettings.supportedPawchiveBaseUrls
                        }
                        .orEmpty()
            onDraftChange(draft.copy(pawchiveBaseUrl = nextBaseUrl))
          },
      )
      if (selectedOption == PawchiveBaseUrlOption.Custom) {
        SettingsStackedInputRow(
            value = draft.pawchiveBaseUrl,
            onValueChange = { onDraftChange(draft.copy(pawchiveBaseUrl = it)) },
            label = stringResource(Res.string.base_url),
            supportingText = stringResource(Res.string.pawchive_base_url_desc),
        )
      }
    }
  }
}

@Composable
private fun AppearanceSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
  SettingsGroup(title = stringResource(Res.string.appearance), framed = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
      SettingsDropdownField(
          label = stringResource(Res.string.theme_mode),
          supportingText = stringResource(Res.string.theme_mode_desc),
          selected = draft.themeMode,
          options = AppSettings.supportedThemeModes,
          optionLabel = { themeModeLabel(it) },
          onSelect = { onDraftChange(draft.copy(themeMode = it)) },
      )
      SettingsDropdownField(
          label = stringResource(Res.string.app_language),
          supportingText = stringResource(Res.string.app_language_desc),
          selected = draft.language,
          options = AppSettings.supportedLanguages,
          optionLabel = { languageLabel(it) },
          onSelect = { onDraftChange(draft.copy(language = it)) },
      )
      SettingsInputRow(
          value = draft.cardWidthInput,
          onValueChange = { onDraftChange(draft.copy(cardWidthInput = it.filter(Char::isDigit))) },
          label = stringResource(Res.string.card_width),
          supportingText = "${AppSettings.CELL_MIN_WIDTH_MIN}-${AppSettings.CELL_MIN_WIDTH_MAX} dp",
      )
    }
  }
}

@Composable
private fun TranslationSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    showApiKey: Boolean,
    onToggleShowApiKey: () -> Unit,
) {
  SettingsGroup(title = stringResource(Res.string.translation), framed = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
      SettingsSwitchRow(
          label = stringResource(Res.string.enable_translation),
          supportingText = stringResource(Res.string.enable_translation_desc),
          checked = draft.translationEnabled,
          onCheckedChange = { onDraftChange(draft.copy(translationEnabled = it)) },
      )
      if (draft.translationEnabled) {
        SettingsDropdownField(
            label = stringResource(Res.string.provider),
            supportingText = stringResource(Res.string.translation_provider_desc),
            selected = draft.translationProvider,
            options = TranslationProvider.entries,
            optionLabel = { translationProviderLabel(it) },
            onSelect = { onDraftChange(draft.copy(translationProvider = it)) },
        )
        SettingsDropdownField(
            label = stringResource(Res.string.translation_target_language),
            supportingText = stringResource(Res.string.translation_target_language_desc),
            selected = draft.translationTargetLanguage,
            options = TranslationTargetLanguage.entries,
            optionLabel = { translationTargetLanguageLabel(it) },
            onSelect = { onDraftChange(draft.copy(translationTargetLanguage = it)) },
        )
        SettingsInputRow(
            value = draft.chunkWordLimitInput,
            onValueChange = {
              onDraftChange(draft.copy(chunkWordLimitInput = it.filter(Char::isDigit)))
            },
            label = stringResource(Res.string.chunk_word_limit),
            supportingText =
                "${AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MIN}-${AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX}",
        )
        SettingsInputRow(
            value = draft.maxConcurrencyInput,
            onValueChange = {
              onDraftChange(draft.copy(maxConcurrencyInput = it.filter(Char::isDigit)))
            },
            label = stringResource(Res.string.max_concurrency),
            supportingText =
                "${AppSettings.TRANSLATION_MAX_CONCURRENCY_MIN}-${AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX}",
        )
        if (draft.translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
          SettingsStackedInputRow(
              value = draft.openAiBaseUrl,
              onValueChange = { onDraftChange(draft.copy(openAiBaseUrl = it)) },
              label = stringResource(Res.string.base_url),
              supportingText = stringResource(Res.string.openai_base_url_desc),
          )
          SettingsStackedInputRow(
              value = draft.openAiApiKey,
              onValueChange = { onDraftChange(draft.copy(openAiApiKey = it)) },
              label = stringResource(Res.string.api_key),
              supportingText = stringResource(Res.string.openai_api_key_desc),
              visualTransformation =
                  if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
          )
          TextButton(onClick = onToggleShowApiKey) {
            Text(
                stringResource(if (showApiKey) Res.string.hide_api_key else Res.string.show_api_key)
            )
          }
          SettingsInputRow(
              value = draft.openAiModel,
              onValueChange = { onDraftChange(draft.copy(openAiModel = it)) },
              label = stringResource(Res.string.model),
              supportingText = stringResource(Res.string.openai_model_desc),
          )
          SettingsStackedInputRow(
              value = draft.openAiPromptTemplate,
              onValueChange = { onDraftChange(draft.copy(openAiPromptTemplate = it)) },
              label = stringResource(Res.string.prompt_template),
              supportingText = stringResource(Res.string.openai_prompt_template_desc),
              singleLine = false,
              minLines = 5,
          )
        }
      }
    }
  }
}

@Composable
private fun DownloadSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    onChooseSavePath: () -> Unit,
) {
  SettingsGroup(title = stringResource(Res.string.download_settings), framed = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
      SettingsNavigationRow(
          title = stringResource(Res.string.download_save_path),
          subtitle =
              draft.downloadSavePath.takeIf { it.isNotBlank() }
                  ?: stringResource(Res.string.download_save_path_not_configured),
          onClick = onChooseSavePath,
      )
      SettingsSwitchRow(
          label = stringResource(Res.string.download_allow_media_indexing),
          supportingText = stringResource(Res.string.download_allow_media_indexing_desc),
          checked = draft.downloadAllowMediaIndexing,
          onCheckedChange = { onDraftChange(draft.copy(downloadAllowMediaIndexing = it)) },
      )
      SettingsDropdownField(
          label = stringResource(Res.string.download_subfolder_mode),
          supportingText = stringResource(Res.string.download_subfolder_mode_desc),
          selected = draft.downloadSubfolderMode,
          options = AppSettings.supportedDownloadSubfolderModes,
          optionLabel = { downloadSubfolderModeLabel(it) },
          onSelect = { onDraftChange(draft.copy(downloadSubfolderMode = it)) },
      )
      SettingsDropdownField(
          label = stringResource(Res.string.download_file_name_mode),
          supportingText = stringResource(Res.string.download_file_name_mode_desc),
          selected = draft.downloadFileNameMode,
          options = AppSettings.supportedDownloadFileNameModes,
          optionLabel = { downloadFileNameModeLabel(it) },
          onSelect = { onDraftChange(draft.copy(downloadFileNameMode = it)) },
      )
      if (draft.downloadFileNameMode == DownloadFileNameMode.CUSTOM) {
        SettingsInputRow(
            value = draft.downloadCustomFileNameTemplate,
            onValueChange = { onDraftChange(draft.copy(downloadCustomFileNameTemplate = it)) },
            label = stringResource(Res.string.download_file_name_template),
            supportingText = stringResource(Res.string.download_file_name_template_desc),
        )
      }
    }
  }
}
