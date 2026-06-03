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

public val Icons.ImageW400Outlined: ImageVector
  get() {
    if (_imageW400Outlined != null) {
      return _imageW400Outlined!!
    }
    _imageW400Outlined =
        Builder(
                name = "ImageW400Outlined",
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
                moveTo(200.0f, 840.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(120.0f, 760.0f)
                verticalLineToRelative(-560.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(200.0f, 120.0f)
                horizontalLineToRelative(560.0f)
                quadToRelative(33.0f, 0.0f, 56.5f, 23.5f)
                reflectiveQuadTo(840.0f, 200.0f)
                verticalLineToRelative(560.0f)
                quadToRelative(0.0f, 33.0f, -23.5f, 56.5f)
                reflectiveQuadTo(760.0f, 840.0f)
                lineTo(200.0f, 840.0f)
                close()
                moveTo(200.0f, 760.0f)
                horizontalLineToRelative(560.0f)
                verticalLineToRelative(-560.0f)
                lineTo(200.0f, 200.0f)
                verticalLineToRelative(560.0f)
                close()
                moveTo(240.0f, 680.0f)
                horizontalLineToRelative(480.0f)
                lineTo(570.0f, 480.0f)
                lineTo(450.0f, 640.0f)
                lineToRelative(-90.0f, -120.0f)
                lineToRelative(-120.0f, 160.0f)
                close()
                moveTo(200.0f, 760.0f)
                verticalLineToRelative(-560.0f)
                verticalLineToRelative(560.0f)
                close()
              }
            }
            .build()
    return _imageW400Outlined!!
  }

private var _imageW400Outlined: ImageVector? = null
