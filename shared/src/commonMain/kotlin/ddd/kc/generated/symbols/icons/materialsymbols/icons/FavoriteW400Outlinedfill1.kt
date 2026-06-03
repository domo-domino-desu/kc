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

public val Icons.FavoriteW400Outlinedfill1: ImageVector
  get() {
    if (_favoriteW400Outlinedfill1 != null) {
      return _favoriteW400Outlinedfill1!!
    }
    _favoriteW400Outlinedfill1 =
        Builder(
                name = "FavoriteW400Outlinedfill1",
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
                moveToRelative(480.0f, 840.0f)
                lineToRelative(-58.0f, -52.0f)
                quadToRelative(-101.0f, -91.0f, -167.0f, -157.0f)
                reflectiveQuadTo(150.0f, 512.5f)
                quadTo(111.0f, 460.0f, 95.5f, 416.0f)
                reflectiveQuadTo(80.0f, 326.0f)
                quadToRelative(0.0f, -94.0f, 63.0f, -157.0f)
                reflectiveQuadToRelative(157.0f, -63.0f)
                quadToRelative(52.0f, 0.0f, 99.0f, 22.0f)
                reflectiveQuadToRelative(81.0f, 62.0f)
                quadToRelative(34.0f, -40.0f, 81.0f, -62.0f)
                reflectiveQuadToRelative(99.0f, -22.0f)
                quadToRelative(94.0f, 0.0f, 157.0f, 63.0f)
                reflectiveQuadToRelative(63.0f, 157.0f)
                quadToRelative(0.0f, 46.0f, -15.5f, 90.0f)
                reflectiveQuadTo(810.0f, 512.5f)
                quadTo(771.0f, 565.0f, 705.0f, 631.0f)
                reflectiveQuadTo(538.0f, 788.0f)
                lineToRelative(-58.0f, 52.0f)
                close()
              }
            }
            .build()
    return _favoriteW400Outlinedfill1!!
  }

private var _favoriteW400Outlinedfill1: ImageVector? = null
