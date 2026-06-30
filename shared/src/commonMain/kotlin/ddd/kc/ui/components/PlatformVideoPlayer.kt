package ddd.kc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.Forward10W400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.Forward30W400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FullscreenExitW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.FullscreenW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.PauseW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.PlayArrowW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.Replay10W400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.Replay30W400Outlined
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

@Composable
fun PlatformVideoPlayer(url: String, modifier: Modifier = Modifier) {
  var loadRequested by remember(url) { mutableStateOf(false) }

  Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
    if (loadRequested) {
      val playerState = rememberVideoPlayerState()
      LaunchedEffect(playerState, url) { playerState.openUri(url, InitialPlayerState.PLAY) }

      VideoPlayerSurface(
          playerState = playerState,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
          overlay = { VideoControlsOverlay(playerState = playerState) },
      )
    } else {
      IconButton(onClick = { loadRequested = true }) {
        Icon(
            imageVector = Icons.PlayArrowW400Outlined,
            contentDescription = null,
            tint = Color.White,
        )
      }
    }
  }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VideoControlsOverlay(playerState: VideoPlayerState) {
  var controlsVisible by remember { mutableStateOf(true) }

  BackHandler(enabled = playerState.isFullscreen) { playerState.toggleFullscreen() }

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier =
            Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
              controlsVisible = !controlsVisible
            }
    )

    if (playerState.isLoading) {
      CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center),
          color = Color.White,
          strokeWidth = 3.dp,
      )
    }

    if (controlsVisible) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(
                      Brush.verticalGradient(
                          listOf(
                              Color.Transparent,
                              Color.Transparent,
                              Color.Black.copy(alpha = 0.78f),
                          )
                      )
                  )
      )

      VideoControlBar(
          playerState = playerState,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .clickable(
                      indication = null,
                      interactionSource = remember { MutableInteractionSource() },
                  ) {}
                  .padding(horizontal = 10.dp, vertical = 8.dp),
      )
    }
  }
}

@Composable
private fun VideoControlBar(playerState: VideoPlayerState, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    Slider(
        value = playerState.sliderPos,
        onValueChange = { playerState.seekStart(it) },
        onValueChangeFinished = { playerState.seekFinished() },
        valueRange = 0f..1000f,
        colors =
            SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
            ),
    )

    Box(modifier = Modifier.fillMaxWidth()) {
      Text(
          text = "${playerState.positionText} / ${playerState.durationText}",
          color = Color.White.copy(alpha = 0.9f),
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.align(Alignment.CenterStart),
      )

      Row(
          modifier = Modifier.align(Alignment.CenterEnd),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        PlaybackSpeedMenu(playerState = playerState)
        IconButton(onClick = { playerState.toggleFullscreen() }) {
          Icon(
              imageVector =
                  if (playerState.isFullscreen) Icons.FullscreenExitW400Outlined
                  else Icons.FullscreenW400Outlined,
              contentDescription = null,
              tint = Color.White,
          )
        }
      }

      Row(
          modifier = Modifier.align(Alignment.Center),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { playerState.seekBySeconds(-30.0) }) {
          Icon(
              imageVector = Icons.Replay30W400Outlined,
              contentDescription = null,
              tint = Color.White,
          )
        }
        IconButton(onClick = { playerState.seekBySeconds(-10.0) }) {
          Icon(
              imageVector = Icons.Replay10W400Outlined,
              contentDescription = null,
              tint = Color.White,
          )
        }
        IconButton(onClick = { playerState.togglePlayback() }) {
          Icon(
              imageVector =
                  if (playerState.isPlaying) Icons.PauseW400Outlined
                  else Icons.PlayArrowW400Outlined,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(32.dp),
          )
        }
        IconButton(onClick = { playerState.seekBySeconds(10.0) }) {
          Icon(
              imageVector = Icons.Forward10W400Outlined,
              contentDescription = null,
              tint = Color.White,
          )
        }
        IconButton(onClick = { playerState.seekBySeconds(30.0) }) {
          Icon(
              imageVector = Icons.Forward30W400Outlined,
              contentDescription = null,
              tint = Color.White,
          )
        }
      }
    }
  }
}

@Composable
private fun PlaybackSpeedMenu(playerState: VideoPlayerState) {
  var expanded by remember { mutableStateOf(false) }
  val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

  Box {
    TextButton(onClick = { expanded = true }) {
      Text(
          text = "${playerState.playbackSpeed.formatSpeed()}x",
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
      )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      speeds.forEach { speed ->
        DropdownMenuItem(
            text = { Text("${speed.formatSpeed()}x") },
            onClick = {
              playerState.playbackSpeed = speed
              expanded = false
            },
        )
      }
    }
  }
}

private fun VideoPlayerState.togglePlayback() {
  if (isPlaying) pause() else play()
}

private fun VideoPlayerState.seekBySeconds(deltaSeconds: Double) {
  val durationSeconds = duration.takeIf { it > 0.0 } ?: return
  val targetSeconds = (currentTime + deltaSeconds).coerceIn(0.0, durationSeconds)
  seekTo((targetSeconds / durationSeconds * 1000.0).toFloat())
}

private fun Float.formatSpeed(): String {
  val rounded = kotlin.math.round(this * 100) / 100
  return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}
