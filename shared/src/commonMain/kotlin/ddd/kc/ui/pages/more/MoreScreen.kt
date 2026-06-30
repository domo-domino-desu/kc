package ddd.kc.ui.pages.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import ddd.kc.LocalActivePlatform
import ddd.kc.LocalAppSettings
import ddd.kc.data.model.Platform
import ddd.kc.data.network.KcSessionStore
import ddd.kc.data.settings.AppSettings.Companion.CELL_MIN_WIDTH_DEFAULT
import ddd.kc.data.settings.AppSettings.Companion.CELL_MIN_WIDTH_MAX
import ddd.kc.data.settings.AppSettings.Companion.CELL_MIN_WIDTH_MIN
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.LogoutW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.MenuW400Outlined
import ddd.kc.i18n.AppLanguage
import ddd.kc.ui.components.LocalShowToast
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.components.platform.rememberPlatformDirectoryPicker
import ddd.kc.ui.pages.about.AboutRouteScreen
import ddd.kc.ui.theme.ThemeMode
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.about
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.choose_save_path
import kc.shared.generated.resources.download_save_path
import kc.shared.generated.resources.download_save_path_not_configured
import kc.shared.generated.resources.favorites
import kc.shared.generated.resources.favorites_requires_login
import kc.shared.generated.resources.grid_cell_width
import kc.shared.generated.resources.language_en
import kc.shared.generated.resources.language_label
import kc.shared.generated.resources.language_system
import kc.shared.generated.resources.language_zh
import kc.shared.generated.resources.logout
import kc.shared.generated.resources.platform_logged_in
import kc.shared.generated.resources.platform_login
import kc.shared.generated.resources.save
import kc.shared.generated.resources.section_account
import kc.shared.generated.resources.section_settings
import kc.shared.generated.resources.theme_mode
import kc.shared.generated.resources.theme_mode_dark
import kc.shared.generated.resources.theme_mode_light
import kc.shared.generated.resources.theme_mode_system
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

object MoreTab : Tab {
  private fun readResolve(): Any = MoreTab

  override val options: TabOptions
    @Composable
    get() =
        TabOptions(
            index = 3u,
            title = "更多",
            icon = rememberVectorPainter(Icons.MenuW400Outlined),
        )

  @Composable
  override fun Content() {
    Navigator(screen = MoreScreen())
  }
}

class MoreScreen(private val repeatSelectionToken: Int = 0) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val appSettings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    val activePlatform = LocalActivePlatform.current
    val sessionStore = koinInject<KcSessionStore>()
    val screenModel = koinInject<MoreScreenModel>()
    val showToast = LocalShowToast.current
    val listState = rememberLazyListState()
    var lastHandledRepeatToken by remember { mutableIntStateOf(repeatSelectionToken) }

    val loggedInPlatforms by sessionStore.loggedInPlatforms.collectAsState()
    val cellWidth by appSettings.cellMinWidthDpFlow().collectAsState(CELL_MIN_WIDTH_DEFAULT)
    val downloadSavePath by
        appSettings.downloadSavePathFlow().collectAsState(appSettings.downloadSavePath())
    val language by appSettings.languageFlow().collectAsState(appSettings.language())
    val themeMode by appSettings.themeModeFlow().collectAsState(appSettings.themeMode())
    val favoritesLoginToast =
        stringResource(Res.string.favorites_requires_login, activePlatform.displayName)
    val triggerDirectoryPicker = rememberPlatformDirectoryPicker { selectedPath ->
      if (selectedPath != null) scope.launch { appSettings.setDownloadSavePath(selectedPath) }
    }

    var showSessionEditor by remember { mutableStateOf(false) }
    var sessionInput by remember {
      mutableStateOf(sessionStore.getSession(Platform.PAWCHIVE).orEmpty())
    }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showGridWidthEditor by remember { mutableStateOf(false) }
    var gridWidthInput by remember { mutableStateOf(cellWidth.toString()) }

    LaunchedEffect(cellWidth) { if (!showGridWidthEditor) gridWidthInput = cellWidth.toString() }
    LaunchedEffect(repeatSelectionToken) {
      if (repeatSelectionToken == lastHandledRepeatToken) return@LaunchedEffect
      lastHandledRepeatToken = repeatSelectionToken
      if (!listState.isAtTop) scope.launch { listState.animateScrollToItem(0) }
    }

    if (showSessionEditor) {
      AlertDialog(
          onDismissRequest = { showSessionEditor = false },
          title = { Text("Pawchive Session Cookie") },
          text = {
            OutlinedTextField(
                value = sessionInput,
                onValueChange = { sessionInput = it },
                singleLine = false,
                minLines = 3,
                label = { Text("session=...") },
                modifier = Modifier.fillMaxWidth(),
            )
          },
          confirmButton = {
            TextButton(
                onClick = {
                  sessionStore.saveSession(Platform.PAWCHIVE, sessionInput)
                  showSessionEditor = false
                }
            ) {
              Text(stringResource(Res.string.save))
            }
          },
          dismissButton = {
            TextButton(onClick = { showSessionEditor = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
      )
    }

    if (showLanguagePicker) {
      val langOptions =
          listOf(
              AppLanguage.SYSTEM to stringResource(Res.string.language_system),
              AppLanguage.ZH_HANS to stringResource(Res.string.language_zh),
              AppLanguage.EN to stringResource(Res.string.language_en),
          )
      AlertDialog(
          onDismissRequest = { showLanguagePicker = false },
          title = { Text(stringResource(Res.string.language_label)) },
          text = {
            Column {
              langOptions.forEach { (lang, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                      RadioButton(
                          selected = language == lang,
                          onClick = {
                            scope.launch { appSettings.setLanguage(lang) }
                            showLanguagePicker = false
                          },
                      )
                    },
                    modifier =
                        Modifier.clickable {
                          scope.launch { appSettings.setLanguage(lang) }
                          showLanguagePicker = false
                        },
                )
              }
            }
          },
          confirmButton = {
            TextButton(onClick = { showLanguagePicker = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
      )
    }

    if (showThemePicker) {
      val themeOptions =
          listOf(
              ThemeMode.SYSTEM to stringResource(Res.string.theme_mode_system),
              ThemeMode.LIGHT to stringResource(Res.string.theme_mode_light),
              ThemeMode.DARK to stringResource(Res.string.theme_mode_dark),
          )
      AlertDialog(
          onDismissRequest = { showThemePicker = false },
          title = { Text(stringResource(Res.string.theme_mode)) },
          text = {
            Column {
              themeOptions.forEach { (mode, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                      RadioButton(
                          selected = themeMode == mode,
                          onClick = {
                            scope.launch { appSettings.setThemeMode(mode) }
                            showThemePicker = false
                          },
                      )
                    },
                    modifier =
                        Modifier.clickable {
                          scope.launch { appSettings.setThemeMode(mode) }
                          showThemePicker = false
                        },
                )
              }
            }
          },
          confirmButton = {
            TextButton(onClick = { showThemePicker = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
      )
    }

    if (showGridWidthEditor) {
      AlertDialog(
          onDismissRequest = { showGridWidthEditor = false },
          title = { Text(stringResource(Res.string.grid_cell_width)) },
          text = {
            Column {
              OutlinedTextField(
                  value = gridWidthInput,
                  onValueChange = { gridWidthInput = it.filter(Char::isDigit) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  label = { Text(stringResource(Res.string.grid_cell_width)) },
                  modifier = Modifier.fillMaxWidth(),
              )
              Text(
                  text = "$CELL_MIN_WIDTH_MIN-$CELL_MIN_WIDTH_MAX dp",
                  modifier = Modifier.padding(top = 8.dp),
              )
            }
          },
          confirmButton = {
            TextButton(
                onClick = {
                  val nextWidth = gridWidthInput.toIntOrNull() ?: cellWidth
                  scope.launch { appSettings.setCellMinWidthDp(nextWidth) }
                  showGridWidthEditor = false
                }
            ) {
              Text(stringResource(Res.string.save))
            }
          },
          dismissButton = {
            TextButton(onClick = { showGridWidthEditor = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
      )
    }

    Scaffold { paddingValues ->
      LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().padding(paddingValues),
          contentPadding = PaddingValues(horizontal = 12.dp),
          verticalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        item {
          Text(
              text = stringResource(Res.string.section_account),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
          )
        }
        item {
          Card(modifier = Modifier.fillMaxWidth()) {
            val isLoggedIn = Platform.PAWCHIVE in loggedInPlatforms
            if (isLoggedIn) {
              ListItem(
                  headlineContent = {
                    Text(
                        stringResource(Res.string.platform_logged_in, Platform.PAWCHIVE.displayName)
                    )
                  },
                  supportingContent = { Text("Session cookie configured") },
                  trailingContent = {
                    IconButton(onClick = { screenModel.logout(Platform.PAWCHIVE) {} }) {
                      Icon(
                          Icons.LogoutW400Outlined,
                          contentDescription = stringResource(Res.string.logout),
                      )
                    }
                  },
                  modifier =
                      Modifier.clickable {
                        sessionInput = sessionStore.getSession(Platform.PAWCHIVE).orEmpty()
                        showSessionEditor = true
                      },
              )
            } else {
              ListItem(
                  headlineContent = {
                    Text(stringResource(Res.string.platform_login, Platform.PAWCHIVE.displayName))
                  },
                  supportingContent = { Text("Paste a Pawchive session cookie") },
                  modifier =
                      Modifier.clickable {
                        sessionInput = ""
                        showSessionEditor = true
                      },
              )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.favorites)) },
                modifier =
                    Modifier.clickable {
                      if (Platform.PAWCHIVE in loggedInPlatforms) {
                        navigator.push(FavoritesScreen(Platform.PAWCHIVE))
                      } else {
                        showToast(favoritesLoginToast)
                      }
                    },
            )
          }
        }
        item {
          Text(
              text = stringResource(Res.string.section_settings),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
          )
        }
        item {
          Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.grid_cell_width)) },
                supportingContent = { Text("${cellWidth} dp") },
                modifier =
                    Modifier.clickable {
                      gridWidthInput = cellWidth.toString()
                      showGridWidthEditor = true
                    },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.download_save_path)) },
                supportingContent = {
                  Text(
                      downloadSavePath.ifBlank {
                        stringResource(Res.string.download_save_path_not_configured)
                      }
                  )
                },
                trailingContent = { Text(stringResource(Res.string.choose_save_path)) },
                modifier = Modifier.clickable { triggerDirectoryPicker() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.theme_mode)) },
                supportingContent = {
                  Text(
                      when (themeMode) {
                        ThemeMode.SYSTEM -> stringResource(Res.string.theme_mode_system)
                        ThemeMode.LIGHT -> stringResource(Res.string.theme_mode_light)
                        ThemeMode.DARK -> stringResource(Res.string.theme_mode_dark)
                      }
                  )
                },
                modifier = Modifier.clickable { showThemePicker = true },
            )
            HorizontalDivider()
            val langLabel =
                when (language) {
                  AppLanguage.SYSTEM -> stringResource(Res.string.language_system)
                  AppLanguage.ZH_HANS -> stringResource(Res.string.language_zh)
                  AppLanguage.EN -> stringResource(Res.string.language_en)
                }
            ListItem(
                headlineContent = { Text(stringResource(Res.string.language_label)) },
                supportingContent = { Text(langLabel) },
                modifier = Modifier.clickable { showLanguagePicker = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.about)) },
                modifier = Modifier.clickable { navigator.push(AboutRouteScreen()) },
            )
          }
        }
      }
    }
  }
}
