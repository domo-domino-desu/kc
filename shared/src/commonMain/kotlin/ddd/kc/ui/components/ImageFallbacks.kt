package ddd.kc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PostTitleCoverFallback(
    title: String,
    modifier: Modifier = Modifier,
) {
  val displayTitle = title.ifBlank { "(untitled)" }
  val base = remember(displayTitle) { stableSeedColor(displayTitle) }
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .clipToBounds()
              .background(
                  Brush.linearGradient(
                      colors =
                          listOf(
                              lerp(base, Color.Black, 0.18f),
                              lerp(base, MaterialTheme.colorScheme.primary, 0.28f),
                              lerp(base, Color.White, 0.16f),
                          ),
                      start = Offset.Zero,
                      end = Offset.Infinite,
                  )
              )
              .padding(18.dp),
      contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
      drawCircle(
          color = Color.White.copy(alpha = 0.10f),
          radius = size.minDimension * 0.42f,
          center = Offset(size.width * 0.82f, size.height * 0.18f),
      )
      drawCircle(
          color = Color.Black.copy(alpha = 0.10f),
          radius = size.minDimension * 0.34f,
          center = Offset(size.width * 0.12f, size.height * 0.92f),
      )
    }
    Text(
        text = displayTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun CreatorBannerFallback(
    avatarSeed: String,
    modifier: Modifier = Modifier,
) {
  val base = remember(avatarSeed) { stableSeedColor(avatarSeed) }
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .clipToBounds()
              .background(
                  Brush.linearGradient(
                      colors =
                          listOf(
                              lerp(base, Color.White, 0.22f),
                              base,
                              lerp(base, Color.Black, 0.32f),
                          ),
                      start = Offset.Zero,
                      end = Offset.Infinite,
                  )
              )
  ) {
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
      drawCircle(
          color = Color.White.copy(alpha = 0.14f),
          radius = size.maxDimension * 0.42f,
          center = Offset(size.width * 0.18f, size.height * 0.25f),
      )
      drawCircle(
          color = Color.Black.copy(alpha = 0.16f),
          radius = size.maxDimension * 0.36f,
          center = Offset(size.width * 0.92f, size.height * 0.78f),
      )
    }
  }
}

private fun stableSeedColor(seed: String): Color {
  val hash = seed.fold(0x45d9f3b) { acc, char -> (acc * 31) xor char.code }
  val red = 72 + (hash and 0x7f)
  val green = 72 + ((hash ushr 8) and 0x7f)
  val blue = 72 + ((hash ushr 16) and 0x7f)
  return Color(red, green, blue)
}
