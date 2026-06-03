package ddd.kc.ui.pages.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.Platform
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.ArrowBackW400Outlined
import ddd.kc.ui.components.ErrorToastEffect
import ddd.kc.ui.navigation.nextRouteInstanceKey
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.back
import kc.shared.generated.resources.login_password
import kc.shared.generated.resources.login_title
import kc.shared.generated.resources.login_username
import org.jetbrains.compose.resources.stringResource

class LoginScreen(
    private val platform: Platform = Platform.KEMONO,
    private val routeKey: String = nextRouteInstanceKey("login"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<LoginScreenModel>()
    val state by screenModel.state.collectAsState()

    ErrorToastEffect(state.error)
    LaunchedEffect(state.success) { if (state.success) navigator.pop() }

    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text(stringResource(Res.string.login_title)) },
              navigationIcon = {
                IconButton(onClick = { navigator.pop() }) {
                  Icon(
                      imageVector = Icons.ArrowBackW400Outlined,
                      contentDescription = stringResource(Res.string.back),
                  )
                }
              },
          )
        },
    ) { paddingValues ->
      Column(
          modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 32.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = { screenModel.onUsernameChanged(it) },
            label = { Text(stringResource(Res.string.login_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = { screenModel.onPasswordChanged(it) },
            label = { Text(stringResource(Res.string.login_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (state.isLoading) {
          CircularProgressIndicator()
        } else {
          Button(
              onClick = { screenModel.login(platform) },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(Res.string.login_title))
          }
        }
      }
    }
  }
}
