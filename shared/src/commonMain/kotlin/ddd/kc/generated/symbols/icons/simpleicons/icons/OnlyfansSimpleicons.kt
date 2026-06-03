package ddd.kc.generated.symbols.icons.simpleicons.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.simpleicons.Icons

public val Icons.OnlyfansSimpleicons: ImageVector
  get() {
    if (_onlyfansSimpleicons != null) {
      return _onlyfansSimpleicons!!
    }
    _onlyfansSimpleicons =
        Builder(
                name = "OnlyfansSimpleicons",
                defaultWidth = 1.0.dp,
                defaultHeight = 1.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
            )
            .apply {
              path(
                  fill = SolidColor(Color(0xFF000000)),
                  stroke = null,
                  strokeLineWidth = 0.0f,
                  strokeLineCap = Butt,
                  strokeLineJoin = Miter,
                  strokeLineMiter = 4.0f,
                  pathFillType = NonZero,
              ) {
                moveTo(24.0f, 4.00f)
                horizontalLineToRelative(-4.02f)
                curveToRelative(-3.45f, 0.0f, -5.3f, 0.20f, -6.75f, 1.96f)
                arcToRelative(8.00f, 8.00f, 0.0f, true, false, 2.10f, 9.21f)
                curveToRelative(3.18f, -0.23f, 5.39f, -2.13f, 6.09f, -5.17f)
                curveToRelative(0.0f, 0.0f, -2.40f, 0.59f, -4.43f, 0.0f)
                curveToRelative(4.02f, -0.78f, 6.33f, -3.04f, 7.01f, -6.00f)
                moveTo(5.61f, 12.00f)
                arcTo(2.39f, 2.39f, 0.0f, false, true, 9.28f, 9.97f)
                arcToRelative(2.97f, 2.97f, 0.0f, false, true, 3.00f, -2.53f)
                horizontalLineToRelative(0.01f)
                curveToRelative(-0.92f, 1.78f, -1.41f, 3.35f, -2.00f, 5.26f)
                arcTo(2.39f, 2.39f, 0.0f, false, true, 5.61f, 12.0f)
                close()
                moveTo(8.00f, 4.00f)
                arcToRelative(8.00f, 8.00f, 0.0f, true, false, 8.00f, 8.00f)
                arcToRelative(8.00f, 8.00f, 0.0f, false, false, -8.00f, -8.00f)
                moveToRelative(0.0f, 10.39f)
                arcTo(2.40f, 2.40f, 0.0f, true, true, 10.40f, 12.0f)
                arcToRelative(2.40f, 2.40f, 0.0f, false, true, -2.40f, 2.40f)
                close()
              }
            }
            .build()
    return _onlyfansSimpleicons!!
  }

private var _onlyfansSimpleicons: ImageVector? = null
