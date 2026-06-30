package ddd.kc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier) {
  val panel = remember(url) { VlcVideoPanel(url) }

  DisposableEffect(panel) {
    panel.play()
    onDispose { panel.release() }
  }

  SwingPanel(
      factory = { panel.root },
      modifier = modifier,
      update = { panel.play() },
  )
}

private class VlcVideoPanel(private val url: String) {
  private val component = runCatching { EmbeddedMediaPlayerComponent() }.getOrNull()

  val root =
      JPanel(BorderLayout()).apply {
        background = Color.BLACK
        if (component != null) {
          add(component, BorderLayout.CENTER)
        } else {
          add(
              JLabel("Video playback unavailable", SwingConstants.CENTER).apply {
                foreground = Color.WHITE
                background = Color.BLACK
                isOpaque = true
              },
              BorderLayout.CENTER,
          )
        }
      }

  fun play() {
    component?.mediaPlayer()?.media()?.play(url)
  }

  fun release() {
    component?.mediaPlayer()?.controls()?.stop()
    component?.release()
  }
}
