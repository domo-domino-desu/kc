package ddd.kc.generated.symbols.icons.lucide.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round as strokeCapRound
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as strokeJoinRound
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.lucide.Icons

public val Icons.SectionLucide: ImageVector
  get() {
    if (_sectionLucide != null) {
      return _sectionLucide!!
    }
    _sectionLucide =
        Builder(
                name = "SectionLucide",
                defaultWidth = 1.0.dp,
                defaultHeight = 1.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
            )
            .apply {
              path(
                  fill = SolidColor(Color(0x00000000)),
                  stroke = SolidColor(Color(0xFF000000)),
                  strokeLineWidth = 2.0f,
                  strokeLineCap = strokeCapRound,
                  strokeLineJoin = strokeJoinRound,
                  strokeLineMiter = 4.0f,
                  pathFillType = NonZero,
              ) {
                moveTo(16.0f, 5.0f)
                arcToRelative(4.0f, 3.0f, 0.0f, false, false, -8.0f, 0.0f)
                curveToRelative(0.0f, 4.0f, 8.0f, 3.0f, 8.0f, 7.0f)
                arcToRelative(4.0f, 3.0f, 0.0f, false, true, -8.0f, 0.0f)
              }
              path(
                  fill = SolidColor(Color(0x00000000)),
                  stroke = SolidColor(Color(0xFF000000)),
                  strokeLineWidth = 2.0f,
                  strokeLineCap = strokeCapRound,
                  strokeLineJoin = strokeJoinRound,
                  strokeLineMiter = 4.0f,
                  pathFillType = NonZero,
              ) {
                moveTo(8.0f, 19.0f)
                arcToRelative(4.0f, 3.0f, 0.0f, false, false, 8.0f, 0.0f)
                curveToRelative(0.0f, -4.0f, -8.0f, -3.0f, -8.0f, -7.0f)
                arcToRelative(4.0f, 3.0f, 0.0f, false, true, 8.0f, 0.0f)
              }
            }
            .build()
    return _sectionLucide!!
  }

private var _sectionLucide: ImageVector? = null
