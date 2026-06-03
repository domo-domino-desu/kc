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

public val Icons.PersonW400Outlinedfill1: ImageVector
  get() {
    if (_personW400Outlinedfill1 != null) {
      return _personW400Outlinedfill1!!
    }
    _personW400Outlinedfill1 =
        Builder(
                name = "PersonW400Outlinedfill1",
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
                moveTo(367.0f, 433.0f)
                quadToRelative(-47.0f, -47.0f, -47.0f, -113.0f)
                reflectiveQuadToRelative(47.0f, -113.0f)
                quadToRelative(47.0f, -47.0f, 113.0f, -47.0f)
                reflectiveQuadToRelative(113.0f, 47.0f)
                quadToRelative(47.0f, 47.0f, 47.0f, 113.0f)
                reflectiveQuadToRelative(-47.0f, 113.0f)
                quadToRelative(-47.0f, 47.0f, -113.0f, 47.0f)
                reflectiveQuadToRelative(-113.0f, -47.0f)
                close()
                moveTo(160.0f, 800.0f)
                verticalLineToRelative(-112.0f)
                quadToRelative(0.0f, -34.0f, 17.5f, -62.5f)
                reflectiveQuadTo(224.0f, 582.0f)
                quadToRelative(62.0f, -31.0f, 126.0f, -46.5f)
                reflectiveQuadTo(480.0f, 520.0f)
                quadToRelative(66.0f, 0.0f, 130.0f, 15.5f)
                reflectiveQuadTo(736.0f, 582.0f)
                quadToRelative(29.0f, 15.0f, 46.5f, 43.5f)
                reflectiveQuadTo(800.0f, 688.0f)
                verticalLineToRelative(112.0f)
                lineTo(160.0f, 800.0f)
                close()
              }
            }
            .build()
    return _personW400Outlinedfill1!!
  }

private var _personW400Outlinedfill1: ImageVector? = null
