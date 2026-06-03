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

public val Icons.CommentW400Outlined: ImageVector
  get() {
    if (_commentW400Outlined != null) {
      return _commentW400Outlined!!
    }
    _commentW400Outlined =
        Builder(
                name = "CommentW400Outlined",
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
                moveTo(240.0f, 560.0f)
                horizontalLineToRelative(480.0f)
                verticalLineToRelative(-80.0f)
                lineTo(240.0f, 480.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(240.0f, 440.0f)
                horizontalLineToRelative(480.0f)
                verticalLineToRelative(-80.0f)
                lineTo(240.0f, 360.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(240.0f, 320.0f)
                horizontalLineToRelative(480.0f)
                verticalLineToRelative(-80.0f)
                lineTo(240.0f, 240.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(880.0f, 880.0f)
                lineTo(720.0f, 720.0f)
                lineTo(160.0f, 720.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(80.0f, 640.0f)
                verticalLineToRelative(-480.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(160.0f, 80.0f)
                horizontalLineToRelative(640.0f)
                quadToRelative(33.0f, 0.0f, 56.5f, 23.5f)
                reflectiveQuadTo(880.0f, 160.0f)
                verticalLineToRelative(720.0f)
                close()
                moveTo(160.0f, 640.0f)
                horizontalLineToRelative(594.0f)
                lineToRelative(46.0f, 45.0f)
                verticalLineToRelative(-525.0f)
                lineTo(160.0f, 160.0f)
                verticalLineToRelative(480.0f)
                close()
                moveTo(160.0f, 640.0f)
                verticalLineToRelative(-480.0f)
                verticalLineToRelative(480.0f)
                close()
              }
            }
            .build()
    return _commentW400Outlined!!
  }

private var _commentW400Outlined: ImageVector? = null
