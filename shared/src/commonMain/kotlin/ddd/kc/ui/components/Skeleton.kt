package ddd.kc.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private const val SKELETON_SHIFT_START = -1f
private const val SKELETON_SHIFT_END = 1f
private const val SKELETON_ANIMATION_MS = 1050
private const val SKELETON_BASE_ALPHA = 0.72f
private const val SKELETON_HIGHLIGHT_ALPHA = 0.1f
private const val SKELETON_BRUSH_START_X = 520f
private const val SKELETON_BRUSH_START_Y = 160f
private const val SKELETON_BRUSH_END_X = 820f
private const val SKELETON_BRUSH_END_Y = 160f
private const val SKELETON_BRUSH_END_X_OFFSET = 420f
private const val SKELETON_BRUSH_END_Y_OFFSET = 220f

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
  val transition = rememberInfiniteTransition(label = "skeleton")
  val shift by
      transition.animateFloat(
          initialValue = SKELETON_SHIFT_START,
          targetValue = SKELETON_SHIFT_END,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(durationMillis = SKELETON_ANIMATION_MS, easing = LinearEasing),
                  repeatMode = RepeatMode.Restart,
              ),
          label = "skeleton-shift",
      )
  val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SKELETON_BASE_ALPHA)
  val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = SKELETON_HIGHLIGHT_ALPHA)
  val brush =
      Brush.linearGradient(
          colors = listOf(base, highlight, base),
          start = Offset(x = shift * SKELETON_BRUSH_START_X, y = shift * SKELETON_BRUSH_START_Y),
          end =
              Offset(
                  x = shift * SKELETON_BRUSH_END_X + SKELETON_BRUSH_END_X_OFFSET,
                  y = shift * SKELETON_BRUSH_END_Y + SKELETON_BRUSH_END_Y_OFFSET,
              ),
      )

  Box(modifier = modifier.clip(shape).background(brush = brush))
}

@Composable
fun GridLoadingSkeleton(
    minCardWidthDp: Int,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    itemCount: Int = 36,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
  val skeletonHeights = remember(itemCount) { List(itemCount) { index -> index to 260.dp } }
  LazyVerticalGrid(
      state = state,
      columns = GridCells.Adaptive(minCardWidthDp.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = contentPadding,
      modifier = modifier.fillMaxSize(),
  ) {
    items(items = skeletonHeights, key = { (index, _) -> index }) { (_, blockHeight) ->
      SkeletonBlock(
          modifier = Modifier.fillMaxWidth().height(blockHeight),
          shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

@Composable
fun ListLoadingSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
    itemHeightDp: Int = 76,
) {
  Column(
      modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(itemCount) {
      SkeletonBlock(
          modifier = Modifier.fillMaxWidth().height(itemHeightDp.dp),
          shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

fun LazyGridScope.gridSkeletonItems(itemCount: Int = 36) {
  val skeletonHeights = List(itemCount) { index -> index to 260.dp }
  items(items = skeletonHeights, key = { (index, _) -> "skeleton-$index" }) { (_, blockHeight) ->
    SkeletonBlock(
        modifier = Modifier.fillMaxWidth().height(blockHeight),
        shape = RoundedCornerShape(14.dp),
    )
  }
}
