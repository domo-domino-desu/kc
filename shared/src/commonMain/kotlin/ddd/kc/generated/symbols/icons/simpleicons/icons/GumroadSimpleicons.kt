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

public val Icons.GumroadSimpleicons: ImageVector
  get() {
    if (_gumroadSimpleicons != null) {
      return _gumroadSimpleicons!!
    }
    _gumroadSimpleicons =
        Builder(
                name = "GumroadSimpleicons",
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
                moveTo(12.0f, 0.0f)
                arcTo(12.0f, 12.0f, 0.0f, false, false, 0.0f, 12.0f)
                arcToRelative(12.0f, 12.0f, 0.0f, false, false, 12.0f, 12.0f)
                arcToRelative(12.0f, 12.0f, 0.0f, false, false, 12.0f, -12.0f)
                arcTo(12.0f, 12.0f, 0.0f, false, false, 12.0f, 0.0f)
                close()
                moveTo(11.99f, 5.12f)
                curveToRelative(4.48f, 0.0f, 6.00f, 3.03f, 6.06f, 4.74f)
                horizontalLineToRelative(-3.24f)
                curveToRelative(-0.07f, -0.96f, -0.90f, -2.41f, -2.90f, -2.41f)
                curveToRelative(-2.14f, 0.0f, -3.51f, 1.86f, -3.51f, 4.13f)
                curveToRelative(0.0f, 2.27f, 1.38f, 4.13f, 3.51f, 4.13f)
                curveToRelative(1.93f, 0.0f, 2.76f, -1.51f, 3.10f, -3.03f)
                horizontalLineToRelative(-3.10f)
                verticalLineToRelative(-1.24f)
                horizontalLineToRelative(6.51f)
                verticalLineToRelative(6.33f)
                horizontalLineToRelative(-2.86f)
                verticalLineToRelative(-3.99f)
                curveToRelative(-0.21f, 1.44f, -1.10f, 4.26f, -4.62f, 4.26f)
                curveToRelative(-3.52f, 0.0f, -5.58f, -2.82f, -5.58f, -6.33f)
                curveToRelative(0.0f, -3.65f, 2.28f, -6.60f, 6.62f, -6.60f)
                close()
              }
            }
            .build()
    return _gumroadSimpleicons!!
  }

private var _gumroadSimpleicons: ImageVector? = null
