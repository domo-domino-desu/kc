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

public val Icons.LockOpenW400Outlined: ImageVector
  get() {
    if (_lockOpenW400Outlined != null) {
      return _lockOpenW400Outlined!!
    }
    _lockOpenW400Outlined =
        Builder(
                name = "LockOpenW400Outlined",
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
                moveTo(240.0f, 320.0f)
                horizontalLineToRelative(360.0f)
                verticalLineToRelative(-80.0f)
                quadToRelative(0.0f, -50.0f, -35.0f, -85.0f)
                reflectiveQuadToRelative(-85.0f, -35.0f)
                quadToRelative(-50.0f, 0.0f, -85.0f, 35.0f)
                reflectiveQuadToRelative(-35.0f, 85.0f)
                horizontalLineToRelative(-80.0f)
                quadToRelative(0.0f, -83.0f, 58.5f, -141.5f)
                reflectiveQuadTo(480.0f, 40.0f)
                quadToRelative(83.0f, 0.0f, 141.5f, 58.5f)
                reflectiveQuadTo(680.0f, 240.0f)
                verticalLineToRelative(80.0f)
                horizontalLineToRelative(40.0f)
                quadToRelative(33.0f, 0.0f, 56.5f, 23.5f)
                reflectiveQuadTo(800.0f, 400.0f)
                verticalLineToRelative(400.0f)
                quadToRelative(0.0f, 33.0f, -23.5f, 56.5f)
                reflectiveQuadTo(720.0f, 880.0f)
                lineTo(240.0f, 880.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(160.0f, 800.0f)
                verticalLineToRelative(-400.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(240.0f, 320.0f)
                close()
                moveTo(240.0f, 800.0f)
                horizontalLineToRelative(480.0f)
                verticalLineToRelative(-400.0f)
                lineTo(240.0f, 400.0f)
                verticalLineToRelative(400.0f)
                close()
                moveTo(536.5f, 656.5f)
                quadTo(560.0f, 633.0f, 560.0f, 600.0f)
                reflectiveQuadToRelative(-23.5f, -56.5f)
                quadTo(513.0f, 520.0f, 480.0f, 520.0f)
                reflectiveQuadToRelative(-56.5f, 23.5f)
                quadTo(400.0f, 567.0f, 400.0f, 600.0f)
                reflectiveQuadToRelative(23.5f, 56.5f)
                quadTo(447.0f, 680.0f, 480.0f, 680.0f)
                reflectiveQuadToRelative(56.5f, -23.5f)
                close()
                moveTo(240.0f, 800.0f)
                verticalLineToRelative(-400.0f)
                verticalLineToRelative(400.0f)
                close()
              }
            }
            .build()
    return _lockOpenW400Outlined!!
  }

private var _lockOpenW400Outlined: ImageVector? = null
