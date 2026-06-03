package ddd.kc.generated.symbols.icons.mdi.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.mdi.Icons

public val Icons.PatreonMdi: ImageVector
  get() {
    if (_patreonMdi != null) {
      return _patreonMdi!!
    }
    _patreonMdi =
        Builder(
                name = "PatreonMdi",
                defaultWidth = 1.0.dp,
                defaultHeight = 1.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
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
                moveTo(14.82f, 2.41f)
                curveToRelative(3.96f, 0.0f, 7.18f, 3.24f, 7.18f, 7.21f)
                curveToRelative(0.0f, 3.96f, -3.22f, 7.18f, -7.18f, 7.18f)
                curveToRelative(-3.97f, 0.0f, -7.21f, -3.22f, -7.21f, -7.18f)
                curveToRelative(0.0f, -3.97f, 3.24f, -7.21f, 7.21f, -7.21f)
                moveTo(2.0f, 21.6f)
                horizontalLineToRelative(3.5f)
                verticalLineTo(2.41f)
                horizontalLineTo(2.0f)
                close()
              }
            }
            .build()
    return _patreonMdi!!
  }

private var _patreonMdi: ImageVector? = null
