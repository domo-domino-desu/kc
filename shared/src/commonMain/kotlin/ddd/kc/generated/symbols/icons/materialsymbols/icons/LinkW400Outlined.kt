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

public val Icons.LinkW400Outlined: ImageVector
  get() {
    if (_linkW400Outlined != null) {
      return _linkW400Outlined!!
    }
    _linkW400Outlined =
        Builder(
                name = "LinkW400Outlined",
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
                moveTo(440.0f, 680.0f)
                lineTo(280.0f, 680.0f)
                quadToRelative(-83.0f, 0.0f, -141.5f, -58.5f)
                reflectiveQuadTo(80.0f, 480.0f)
                quadToRelative(0.0f, -83.0f, 58.5f, -141.5f)
                reflectiveQuadTo(280.0f, 280.0f)
                horizontalLineToRelative(160.0f)
                verticalLineToRelative(80.0f)
                lineTo(280.0f, 360.0f)
                quadToRelative(-50.0f, 0.0f, -85.0f, 35.0f)
                reflectiveQuadToRelative(-35.0f, 85.0f)
                quadToRelative(0.0f, 50.0f, 35.0f, 85.0f)
                reflectiveQuadToRelative(85.0f, 35.0f)
                horizontalLineToRelative(160.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(320.0f, 520.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(320.0f)
                verticalLineToRelative(80.0f)
                lineTo(320.0f, 520.0f)
                close()
                moveTo(520.0f, 680.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(160.0f)
                quadToRelative(50.0f, 0.0f, 85.0f, -35.0f)
                reflectiveQuadToRelative(35.0f, -85.0f)
                quadToRelative(0.0f, -50.0f, -35.0f, -85.0f)
                reflectiveQuadToRelative(-85.0f, -35.0f)
                lineTo(520.0f, 360.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(160.0f)
                quadToRelative(83.0f, 0.0f, 141.5f, 58.5f)
                reflectiveQuadTo(880.0f, 480.0f)
                quadToRelative(0.0f, 83.0f, -58.5f, 141.5f)
                reflectiveQuadTo(680.0f, 680.0f)
                lineTo(520.0f, 680.0f)
                close()
              }
            }
            .build()
    return _linkW400Outlined!!
  }

private var _linkW400Outlined: ImageVector? = null
