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

public val Icons.BoostySimpleicons: ImageVector
  get() {
    if (_boostySimpleicons != null) {
      return _boostySimpleicons!!
    }
    _boostySimpleicons =
        Builder(
                name = "BoostySimpleicons",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
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
                moveTo(2.66f, 14.34f)
                lineTo(6.80f, 0.0f)
                horizontalLineToRelative(6.36f)
                lineTo(11.88f, 4.44f)
                lineToRelative(-0.04f, 0.08f)
                lineToRelative(-3.38f, 11.73f)
                horizontalLineToRelative(3.15f)
                curveToRelative(-1.32f, 3.29f, -2.35f, 5.87f, -3.09f, 7.73f)
                curveToRelative(-5.82f, -0.06f, -7.44f, -4.23f, -6.02f, -9.16f)
                moveTo(8.55f, 24.0f)
                lineToRelative(7.67f, -11.04f)
                horizontalLineToRelative(-3.25f)
                lineToRelative(2.83f, -7.07f)
                curveToRelative(4.85f, 0.51f, 7.14f, 4.33f, 5.79f, 8.95f)
                curveTo(20.16f, 19.81f, 14.34f, 24.0f, 8.68f, 24.0f)
                horizontalLineToRelative(-0.13f)
                close()
              }
            }
            .build()
    return _boostySimpleicons!!
  }

private var _boostySimpleicons: ImageVector? = null
