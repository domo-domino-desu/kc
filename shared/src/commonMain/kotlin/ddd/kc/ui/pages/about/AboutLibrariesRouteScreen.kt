package ddd.kc.ui.pages.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import ddd.kc.ui.app.navigation.AppScreen
import ddd.kc.ui.components.DetailAppBar
import ddd.kc.utils.coroutines.resultOfSuspend
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.about_libraries
import kc.shared.generated.resources.about_libraries_load_failed
import kc.shared.generated.resources.about_libraries_metadata_source
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable
class AboutLibrariesRouteScreen : AppScreen {
  override val key: String = "about-libraries-route"

  @Composable
  override fun Content() {
    val metadata by
        produceState<Result<String>?>(initialValue = null) {
          value = resultOfSuspend { loadAboutLibrariesJson() }
        }
    Column(modifier = Modifier.fillMaxSize()) {
      DetailAppBar(title = stringResource(Res.string.about_libraries))
      when (val result = metadata) {
        null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
        else ->
            result.fold(
                onSuccess = { AboutLibrariesContent(it) },
                onFailure = {
                  Text(
                      stringResource(Res.string.about_libraries_load_failed),
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.padding(16.dp),
                  )
                },
            )
      }
    }
  }
}

@Composable
private fun AboutLibrariesContent(json: String) {
  val libraries by produceLibraries { json }
  LibrariesContainer(
      libraries = libraries,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      footer = {
        item {
          Text(
              text = stringResource(Res.string.about_libraries_metadata_source),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
          )
        }
      },
  )
}
