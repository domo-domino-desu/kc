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

public val Icons.ExpandMoreW400Outlined: ImageVector
  get() {
    if (_expandMoreW400Outlined != null) {
      return _expandMoreW400Outlined!!
    }
    _expandMoreW400Outlined =
        Builder(
                name = "ExpandMoreW400Outlined",
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
                moveTo(480.0f, 615.0f)
                lineTo(240.0f, 375.0f)
                lineToRelative(56.0f, -56.0f)
                lineToRelative(184.0f, 184.0f)
                lineToRelative(184.0f, -184.0f)
                lineToRelative(56.0f, 56.0f)
                lineToRelative(-240.0f, 240.0f)
                close()
              }
            }
            .build()
    return _expandMoreW400Outlined!!
  }

private var _expandMoreW400Outlined: ImageVector? = null
