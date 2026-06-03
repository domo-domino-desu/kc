package ddd.kc.ui.pages.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import ddd.kc.ui.components.DetailAppBar
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.about_libraries
import kc.shared.generated.resources.about_libraries_metadata_source
import org.jetbrains.compose.resources.stringResource

class AboutLibrariesRouteScreen : Screen {
  override val key: String = "about-libraries-route"

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val libraries by produceLibraries { loadAboutLibrariesJson() }
    Column(modifier = Modifier.fillMaxSize()) {
      DetailAppBar(title = stringResource(Res.string.about_libraries))

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
  }
}
