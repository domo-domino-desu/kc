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

public val Icons.CodeW400Outlined: ImageVector
  get() {
    if (_codeW400Outlined != null) {
      return _codeW400Outlined!!
    }
    _codeW400Outlined =
        Builder(
                name = "CodeW400Outlined",
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
                moveTo(320.0f, 720.0f)
                lineTo(80.0f, 480.0f)
                lineToRelative(240.0f, -240.0f)
                lineToRelative(57.0f, 57.0f)
                lineToRelative(-184.0f, 184.0f)
                lineToRelative(183.0f, 183.0f)
                lineToRelative(-56.0f, 56.0f)
                close()
                moveTo(640.0f, 720.0f)
                lineTo(583.0f, 663.0f)
                lineTo(767.0f, 479.0f)
                lineTo(584.0f, 296.0f)
                lineTo(640.0f, 240.0f)
                lineTo(880.0f, 480.0f)
                lineTo(640.0f, 720.0f)
                close()
              }
            }
            .build()
    return _codeW400Outlined!!
  }

private var _codeW400Outlined: ImageVector? = null
