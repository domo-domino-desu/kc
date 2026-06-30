package ddd.kc.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier) {
  val videoViewState = remember { mutableStateOf<VideoView?>(null) }

  AndroidView(
      modifier = modifier,
      factory = { context ->
        VideoView(context).apply {
          val controller = MediaController(context)
          controller.setAnchorView(this)
          setMediaController(controller)
          videoViewState.value = this
        }
      },
      update = { view ->
        if (view.tag != url) {
          view.tag = url
          view.setVideoURI(Uri.parse(url))
          view.setOnPreparedListener { player ->
            player.isLooping = false
            view.start()
          }
        }
      },
  )

  DisposableEffect(url) {
    onDispose {
      videoViewState.value?.stopPlayback()
      videoViewState.value = null
    }
  }
}
