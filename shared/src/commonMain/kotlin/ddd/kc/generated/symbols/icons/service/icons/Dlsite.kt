package ddd.kc.generated.symbols.icons.service.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.service.Icons

public val Icons.Dlsite: ImageVector
  get() {
    if (_dlsite != null) {
      return _dlsite!!
    }
    _dlsite =
        Builder(
                name = "Dlsite",
                defaultWidth = 80.0.dp,
                defaultHeight = 80.0.dp,
                viewportWidth = 80.0f,
                viewportHeight = 80.0f,
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
                moveTo(60.55f, 8.06f)
                horizontalLineTo(48.14f)
                verticalLineToRelative(61.24f)
                horizontalLineTo(80.0f)
                verticalLineTo(56.24f)
                horizontalLineTo(60.55f)
                close()
                moveTo(11.63f, 8.0f)
                horizontalLineTo(0.0f)
                verticalLineToRelative(61.35f)
                horizontalLineToRelative(14.01f)
                arcToRelative(28.14f, 28.14f, 0.0f, false, false, 19.93f, -8.38f)
                arcToRelative(32.03f, 32.03f, 0.0f, false, false, 9.21f, -22.52f)
                curveTo(43.09f, 21.70f, 28.92f, 8.0f, 11.63f, 8.0f)
                moveToRelative(14.15f, 42.83f)
                arcToRelative(19.97f, 19.97f, 0.0f, false, true, -12.84f, 5.56f)
                horizontalLineToRelative(-0.42f)
                verticalLineTo(20.92f)
                curveToRelative(3.21f, 0.44f, 17.48f, 3.20f, 17.78f, 17.44f)
                curveToRelative(0.09f, 5.11f, -1.42f, 9.35f, -4.51f, 12.47f)
              }
            }
            .build()
    return _dlsite!!
  }

private var _dlsite: ImageVector? = null
