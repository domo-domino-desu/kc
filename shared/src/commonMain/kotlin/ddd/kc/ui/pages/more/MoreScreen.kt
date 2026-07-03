package ddd.kc.ui.pages.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import ddd.kc.data.model.Platform
import ddd.kc.data.network.KcSessionStore
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.AttributionW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.LogoutW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.MenuW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.PersonW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ReceiptLongW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.SettingsW400Outlined
import ddd.kc.ui.app.LocalActivePlatform
import ddd.kc.ui.components.LocalShowToast
import ddd.kc.ui.components.isAtTop
import ddd.kc.ui.pages.about.AboutRouteScreen
import ddd.kc.ui.pages.history.HistoryRouteScreen
import ddd.kc.ui.pages.settings.SettingsRouteScreen
import ddd.kc.ui.pages.settings.components.SettingsGroup
import ddd.kc.ui.pages.settings.components.SettingsListItem
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.about
import kc.shared.generated.resources.about_summary
import kc.shared.generated.resources.cancel
import kc.shared.generated.resources.current_account
import kc.shared.generated.resources.favorites
import kc.shared.generated.resources.favorites_requires_login
import kc.shared.generated.resources.history
import kc.shared.generated.resources.history_summary
import kc.shared.generated.resources.logout
import kc.shared.generated.resources.logout_confirm_body
import kc.shared.generated.resources.logout_confirm_title
import kc.shared.generated.resources.platform_logged_in
import kc.shared.generated.resources.platform_login
import kc.shared.generated.resources.save
import kc.shared.generated.resources.session_cookie_configured
import kc.shared.generated.resources.session_cookie_not_configured
import kc.shared.generated.resources.settings
import kc.shared.generated.resources.settings_summary
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

class MoreScreen(
    private val onReselectHandlerChanged: (((() -> Unit)?) -> Unit) = {},
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val activePlatform = LocalActivePlatform.current
    val sessionStore = koinInject<KcSessionStore>()
    val screenModel = koinInject<MoreScreenModel>()
    val showToast = LocalShowToast.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val loggedInPlatforms by sessionStore.loggedInPlatforms.collectAsState()
    val isLoggedIn = activePlatform in loggedInPlatforms
    val favoritesLoginToast =
        stringResource(Res.string.favorites_requires_login, activePlatform.displayName)

    var showSessionEditor by remember { mutableStateOf(false) }
    var sessionInput by remember {
      mutableStateOf(sessionStore.getSession(Platform.PAWCHIVE).orEmpty())
    }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val latestOnReselect by rememberUpdatedState {
      if (!listState.isAtTop) scope.launch { listState.animateScrollToItem(0) }
    }

    DisposableEffect(onReselectHandlerChanged) {
      val handler = { latestOnReselect() }
      onReselectHandlerChanged(handler)
      onDispose { onReselectHandlerChanged(null) }
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

    if (showLogoutDialog) {
      AlertDialog(
          onDismissRequest = { showLogoutDialog = false },
          title = { Text(stringResource(Res.string.logout_confirm_title)) },
          text = { Text(stringResource(Res.string.logout_confirm_body)) },
          confirmButton = {
            TextButton(
                onClick = {
                  showLogoutDialog = false
                  screenModel.logout(activePlatform) { error -> showToast(error) }
                }
            ) {
              Text(stringResource(Res.string.logout))
            }
          },
          dismissButton = {
            TextButton(onClick = { showLogoutDialog = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
      )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        SettingsGroup(titleHorizontalPadding = 0.dp, containerHorizontalPadding = 0.dp) {
          SettingsListItem(
              icon = Icons.PersonW400Outlined,
              title = stringResource(Res.string.current_account),
              subtitle =
                  if (isLoggedIn) {
                    stringResource(Res.string.platform_logged_in, activePlatform.displayName) +
                        " · " +
                        stringResource(Res.string.session_cookie_configured)
                  } else {
                    stringResource(Res.string.platform_login, activePlatform.displayName) +
                        " · " +
                        stringResource(Res.string.session_cookie_not_configured)
                  },
              onClick = {
                sessionInput = sessionStore.getSession(Platform.PAWCHIVE).orEmpty()
                showSessionEditor = true
              },
          )
          SettingsListItem(
              icon = Icons.LogoutW400Outlined,
              title = stringResource(Res.string.logout),
              subtitle = stringResource(Res.string.logout_confirm_body),
              onClick = { showLogoutDialog = true },
              enabled = isLoggedIn,
              showDivider = false,
          )
        }
      }
      item {
        SettingsGroup(titleHorizontalPadding = 0.dp, containerHorizontalPadding = 0.dp) {
          SettingsListItem(
              icon = Icons.ReceiptLongW400Outlined,
              title = stringResource(Res.string.history),
              subtitle = stringResource(Res.string.history_summary),
              onClick = { navigator.push(HistoryRouteScreen(activePlatform)) },
          )
          SettingsListItem(
              icon = Icons.FavoriteW400Outlined,
              title = stringResource(Res.string.favorites),
              subtitle = stringResource(Res.string.favorites),
              onClick = {
                if (isLoggedIn) navigator.push(FavoritesScreen(activePlatform))
                else showToast(favoritesLoginToast)
              },
          )
          SettingsListItem(
              icon = Icons.SettingsW400Outlined,
              title = stringResource(Res.string.settings),
              subtitle = stringResource(Res.string.settings_summary),
              onClick = { navigator.push(SettingsRouteScreen()) },
          )
          SettingsListItem(
              icon = Icons.AttributionW400Outlined,
              title = stringResource(Res.string.about),
              subtitle = stringResource(Res.string.about_summary),
              onClick = { navigator.push(AboutRouteScreen()) },
              showDivider = false,
          )
        }
      }
    }
  }
}
