package ddd.kc.generated.symbols.icons.materialsymbols.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.materialsymbols.Icons

public val Icons.RefreshW400Outlined: ImageVector
  get() {
    if (_refreshW400Outlined != null) {
      return _refreshW400Outlined!!
    }
    _refreshW400Outlined =
        Builder(
                name = "RefreshW400Outlined",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 960.0f,
                viewportHeight = 960.0f,
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
                moveTo(480.0f, 800.0f)
                quadToRelative(-134.0f, 0.0f, -227.0f, -93.0f)
                reflectiveQuadToRelative(-93.0f, -227.0f)
                quadToRelative(0.0f, -134.0f, 93.0f, -227.0f)
                reflectiveQuadToRelative(227.0f, -93.0f)
                quadToRelative(69.0f, 0.0f, 132.0f, 28.5f)
                reflectiveQuadTo(720.0f, 270.0f)
                verticalLineToRelative(-110.0f)
                horizontalLineToRelative(80.0f)
                verticalLineToRelative(280.0f)
                lineTo(520.0f, 440.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(168.0f)
                quadToRelative(-32.0f, -56.0f, -87.5f, -88.0f)
                reflectiveQuadTo(480.0f, 240.0f)
                quadToRelative(-100.0f, 0.0f, -170.0f, 70.0f)
                reflectiveQuadToRelative(-70.0f, 170.0f)
                quadToRelative(0.0f, 100.0f, 70.0f, 170.0f)
                reflectiveQuadToRelative(170.0f, 70.0f)
                quadToRelative(77.0f, 0.0f, 139.0f, -44.0f)
                reflectiveQuadToRelative(87.0f, -116.0f)
                horizontalLineToRelative(84.0f)
                quadToRelative(-28.0f, 106.0f, -114.0f, 173.0f)
                reflectiveQuadToRelative(-196.0f, 67.0f)
                close()
              }
            }
            .build()
    return _refreshW400Outlined!!
  }

private var _refreshW400Outlined: ImageVector? = null
