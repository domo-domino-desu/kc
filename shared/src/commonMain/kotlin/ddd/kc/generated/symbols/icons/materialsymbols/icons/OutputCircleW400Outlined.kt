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

public val Icons.OutputCircleW400Outlined: ImageVector
  get() {
    if (_outputCircleW400Outlined != null) {
      return _outputCircleW400Outlined!!
    }
    _outputCircleW400Outlined =
        Builder(
                name = "OutputCircleW400Outlined",
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
                moveTo(480.0f, 880.0f)
                lineTo(280.0f, 680.0f)
                lineToRelative(56.0f, -56.0f)
                lineToRelative(104.0f, 103.0f)
                verticalLineToRelative(-407.0f)
                horizontalLineToRelative(80.0f)
                verticalLineToRelative(407.0f)
                lineToRelative(104.0f, -103.0f)
                lineToRelative(56.0f, 56.0f)
                lineTo(480.0f, 880.0f)
                close()
                moveTo(146.0f, 700.0f)
                quadToRelative(-32.0f, -49.0f, -49.0f, -105.0f)
                reflectiveQuadTo(80.0f, 480.0f)
                quadToRelative(0.0f, -83.0f, 31.5f, -156.0f)
                reflectiveQuadTo(197.0f, 197.0f)
                quadToRelative(54.0f, -54.0f, 127.0f, -85.5f)
                reflectiveQuadTo(480.0f, 80.0f)
                quadToRelative(83.0f, 0.0f, 156.0f, 31.5f)
                reflectiveQuadTo(763.0f, 197.0f)
                quadToRelative(54.0f, 54.0f, 85.5f, 127.0f)
                reflectiveQuadTo(880.0f, 480.0f)
                quadToRelative(0.0f, 59.0f, -17.0f, 115.0f)
                reflectiveQuadToRelative(-49.0f, 105.0f)
                lineToRelative(-58.0f, -58.0f)
                quadToRelative(22.0f, -37.0f, 33.0f, -78.0f)
                reflectiveQuadToRelative(11.0f, -84.0f)
                quadToRelative(0.0f, -134.0f, -93.0f, -227.0f)
                reflectiveQuadToRelative(-227.0f, -93.0f)
                quadToRelative(-134.0f, 0.0f, -227.0f, 93.0f)
                reflectiveQuadToRelative(-93.0f, 227.0f)
                quadToRelative(0.0f, 43.0f, 11.0f, 84.0f)
                reflectiveQuadToRelative(33.0f, 78.0f)
                lineToRelative(-58.0f, 58.0f)
                close()
              }
            }
            .build()
    return _outputCircleW400Outlined!!
  }

private var _outputCircleW400Outlined: ImageVector? = null
