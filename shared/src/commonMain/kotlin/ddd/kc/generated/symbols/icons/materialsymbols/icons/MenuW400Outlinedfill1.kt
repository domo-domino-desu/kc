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

public val Icons.MenuW400Outlinedfill1: ImageVector
  get() {
    if (_menuW400Outlinedfill1 != null) {
      return _menuW400Outlinedfill1!!
    }
    _menuW400Outlinedfill1 =
        Builder(
                name = "MenuW400Outlinedfill1",
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
                moveTo(120.0f, 720.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(720.0f)
                verticalLineToRelative(80.0f)
                lineTo(120.0f, 720.0f)
                close()
                moveTo(120.0f, 520.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(720.0f)
                verticalLineToRelative(80.0f)
                lineTo(120.0f, 520.0f)
                close()
                moveTo(120.0f, 320.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(720.0f)
                verticalLineToRelative(80.0f)
                lineTo(120.0f, 320.0f)
                close()
              }
            }
            .build()
    return _menuW400Outlinedfill1!!
  }

private var _menuW400Outlinedfill1: ImageVector? = null
