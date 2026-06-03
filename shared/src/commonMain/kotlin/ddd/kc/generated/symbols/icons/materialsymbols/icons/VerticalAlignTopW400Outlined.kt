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

public val Icons.VerticalAlignTopW400Outlined: ImageVector
  get() {
    if (_verticalAlignTopW400Outlined != null) {
      return _verticalAlignTopW400Outlined!!
    }
    _verticalAlignTopW400Outlined =
        Builder(
                name = "VerticalAlignTopW400Outlined",
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
                moveTo(160.0f, 200.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(640.0f)
                verticalLineToRelative(80.0f)
                lineTo(160.0f, 200.0f)
                close()
                moveTo(440.0f, 840.0f)
                verticalLineToRelative(-408.0f)
                lineTo(336.0f, 536.0f)
                lineToRelative(-56.0f, -56.0f)
                lineToRelative(200.0f, -200.0f)
                lineToRelative(200.0f, 200.0f)
                lineToRelative(-56.0f, 56.0f)
                lineToRelative(-104.0f, -104.0f)
                verticalLineToRelative(408.0f)
                horizontalLineToRelative(-80.0f)
                close()
              }
            }
            .build()
    return _verticalAlignTopW400Outlined!!
  }

private var _verticalAlignTopW400Outlined: ImageVector? = null
