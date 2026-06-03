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

public val Icons.ArrowUpwardW400Outlined: ImageVector
  get() {
    if (_arrowUpwardW400Outlined != null) {
      return _arrowUpwardW400Outlined!!
    }
    _arrowUpwardW400Outlined =
        Builder(
                name = "ArrowUpwardW400Outlined",
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
                moveTo(440.0f, 800.0f)
                verticalLineToRelative(-487.0f)
                lineTo(216.0f, 537.0f)
                lineToRelative(-56.0f, -57.0f)
                lineToRelative(320.0f, -320.0f)
                lineToRelative(320.0f, 320.0f)
                lineToRelative(-56.0f, 57.0f)
                lineToRelative(-224.0f, -224.0f)
                verticalLineToRelative(487.0f)
                horizontalLineToRelative(-80.0f)
                close()
              }
            }
            .build()
    return _arrowUpwardW400Outlined!!
  }

private var _arrowUpwardW400Outlined: ImageVector? = null
