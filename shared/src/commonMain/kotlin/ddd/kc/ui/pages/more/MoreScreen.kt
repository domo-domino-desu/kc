package ddd.kc.ui.pages.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.AttributionW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FavoriteW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.LogoutW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.MenuW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.PersonW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ReceiptLongW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.SettingsW400Outlined
import ddd.kc.ui.app.i18n.localizedMessage
import ddd.kc.ui.app.navigation.AppScreen
import ddd.kc.ui.components.ErrorToastEffect
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
import kc.shared.generated.resources.login_method_account
import kc.shared.generated.resources.login_method_session
import kc.shared.generated.resources.login_password
import kc.shared.generated.resources.login_title
import kc.shared.generated.resources.login_username
import kc.shared.generated.resources.logout
import kc.shared.generated.resources.logout_confirm_body
import kc.shared.generated.resources.logout_confirm_title
import kc.shared.generated.resources.more
import kc.shared.generated.resources.platform_logged_in
import kc.shared.generated.resources.platform_login
import kc.shared.generated.resources.save
import kc.shared.generated.resources.session_cookie_hint
import kc.shared.generated.resources.settings
import kc.shared.generated.resources.settings_summary
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private enum class LoginMethod {
  Account,
  SessionCookie,
}

object MoreTab : Tab {
  private fun readResolve(): Any = MoreTab

  override val options: TabOptions
    @Composable
    get() =
        TabOptions(
            index = 3u,
            title = stringResource(Res.string.more),
            icon = rememberVectorPainter(Icons.MenuW400Outlined),
        )

  @Composable
  override fun Content() {
    Navigator(screen = MoreScreen())
  }
}

@Serializable
class MoreScreen : AppScreen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val onReselectHandlerChanged = ddd.kc.ui.app.navigation.LocalRootTabReselectRegistration.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinInject<MoreScreenModel>()
    val showToast = LocalShowToast.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sessionState by screenModel.state.collectAsState()
    val isLoggedIn = sessionState.loggedIn
    val favoritesLoginToast = stringResource(Res.string.favorites_requires_login, "Pawchive")

    var showSessionEditor by remember { mutableStateOf(false) }
    var loginMethod by remember { mutableStateOf(LoginMethod.Account) }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var sessionInput by remember { mutableStateOf(sessionState.session) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val latestOnReselect by rememberUpdatedState {
      if (!listState.isAtTop) scope.launch { listState.animateScrollToItem(0) }
    }
    ErrorToastEffect(sessionState.error?.localizedMessage())
    LaunchedEffect(sessionState.loginSucceeded) {
      if (sessionState.loginSucceeded) {
        showSessionEditor = false
        passwordInput = ""
        screenModel.consumeLoginSuccess()
      }
    }

    DisposableEffect(onReselectHandlerChanged) {
      val handler = { latestOnReselect() }
      onReselectHandlerChanged(handler)
      onDispose { onReselectHandlerChanged(null) }
    }

    if (showSessionEditor) {
      AlertDialog(
          onDismissRequest = {
            if (!sessionState.operationInProgress) {
              showSessionEditor = false
              passwordInput = ""
              screenModel.clearError()
            }
          },
          title = { Text(stringResource(Res.string.login_title)) },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
              val loginMethodLabels =
                  listOf(
                      stringResource(Res.string.login_method_account),
                      stringResource(Res.string.login_method_session),
                  )
              ButtonGroup(
                  overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                  horizontalArrangement =
                      Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                loginMethodLabels.forEachIndexed { index, label ->
                  val method = LoginMethod.entries[index]
                  toggleableItem(
                      checked = loginMethod == method,
                      label = label,
                      onCheckedChange = {
                        if (!sessionState.operationInProgress) {
                          loginMethod = method
                          if (method == LoginMethod.SessionCookie) passwordInput = ""
                          screenModel.clearError()
                        }
                      },
                      weight = 1f,
                  )
                }
              }

              when (loginMethod) {
                LoginMethod.Account -> {
                  OutlinedTextField(
                      value = usernameInput,
                      onValueChange = { usernameInput = it },
                      singleLine = true,
                      enabled = !sessionState.operationInProgress,
                      label = { Text(stringResource(Res.string.login_username)) },
                      modifier = Modifier.fillMaxWidth(),
                  )
                  OutlinedTextField(
                      value = passwordInput,
                      onValueChange = { passwordInput = it },
                      singleLine = true,
                      enabled = !sessionState.operationInProgress,
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                      visualTransformation = PasswordVisualTransformation(),
                      label = { Text(stringResource(Res.string.login_password)) },
                      modifier = Modifier.fillMaxWidth(),
                  )
                }

                LoginMethod.SessionCookie ->
                    OutlinedTextField(
                        value = sessionInput,
                        onValueChange = { sessionInput = it },
                        singleLine = false,
                        enabled = !sessionState.operationInProgress,
                        minLines = 3,
                        label = { Text(stringResource(Res.string.session_cookie_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
              }
            }
          },
          confirmButton = {
            TextButton(
                enabled =
                    !sessionState.operationInProgress &&
                        (loginMethod == LoginMethod.SessionCookie ||
                            (usernameInput.isNotBlank() && passwordInput.isNotBlank())),
                onClick = {
                  when (loginMethod) {
                    LoginMethod.Account -> screenModel.login(usernameInput, passwordInput)
                    LoginMethod.SessionCookie -> {
                      screenModel.saveSession(sessionInput)
                      showSessionEditor = false
                    }
                  }
                },
            ) {
              if (sessionState.operationInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Text(
                    stringResource(
                        if (loginMethod == LoginMethod.Account) Res.string.login_title
                        else Res.string.save
                    )
                )
              }
            }
          },
          dismissButton = {
            TextButton(
                enabled = !sessionState.operationInProgress,
                onClick = {
                  showSessionEditor = false
                  passwordInput = ""
                  screenModel.clearError()
                },
            ) {
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
                  screenModel.logout()
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
                    stringResource(Res.string.platform_logged_in, "Pawchive")
                  } else {
                    stringResource(Res.string.platform_login, "Pawchive")
                  },
              onClick = {
                loginMethod = LoginMethod.Account
                passwordInput = ""
                sessionInput = sessionState.session
                screenModel.clearError()
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
              onClick = { navigator.push(HistoryRouteScreen()) },
          )
          SettingsListItem(
              icon = Icons.FavoriteW400Outlined,
              title = stringResource(Res.string.favorites),
              subtitle = stringResource(Res.string.favorites),
              onClick = {
                if (isLoggedIn) navigator.push(FavoritesScreen())
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
