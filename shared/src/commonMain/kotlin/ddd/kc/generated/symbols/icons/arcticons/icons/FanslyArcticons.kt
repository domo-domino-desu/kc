package ddd.kc.generated.symbols.icons.arcticons.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round as strokeCapRound
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as strokeJoinRound
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.arcticons.Icons

public val Icons.FanslyArcticons: ImageVector
  get() {
    if (_fanslyArcticons != null) {
      return _fanslyArcticons!!
    }
    _fanslyArcticons =
        Builder(
                name = "FanslyArcticons",
                defaultWidth = 1.0.dp,
                defaultHeight = 1.0.dp,
                viewportWidth = 48.0f,
                viewportHeight = 48.0f,
            )
            .apply {
              path(
                  fill = SolidColor(Color(0x00000000)),
                  stroke = SolidColor(Color(0xFF000000)),
                  strokeLineWidth = 1.0f,
                  strokeLineCap = strokeCapRound,
                  strokeLineJoin = strokeJoinRound,
                  strokeLineMiter = 4.0f,
                  pathFillType = NonZero,
              ) {
                moveToRelative(24.0f, 14.14f)
                lineToRelative(13.32f, 13.32f)
                lineToRelative(-12.06f, 12.05f)
                arcToRelative(1.79f, 1.79f, 0.0f, false, true, -2.53f, 0.0f)
                lineTo(10.68f, 27.46f)
                close()
              }
              path(
                  fill = SolidColor(Color(0x00000000)),
                  stroke = SolidColor(Color(0xFF000000)),
                  strokeLineWidth = 1.0f,
                  strokeLineCap = strokeCapRound,
                  strokeLineJoin = strokeJoinRound,
                  strokeLineMiter = 4.0f,
                  pathFillType = NonZero,
              ) {
                moveTo(24.0f, 27.46f)
                moveToRelative(-4.30f, 0.0f)
                arcToRelative(4.30f, 4.30f, 0.0f, true, true, 8.61f, 0.0f)
                arcToRelative(4.30f, 4.30f, 0.0f, true, true, -8.61f, 0.0f)
              }
              path(
                  fill = SolidColor(Color(0x00000000)),
                  stroke = SolidColor(Color(0xFF000000)),
                  strokeLineWidth = 1.0f,
                  strokeLineCap = strokeCapRound,
                  strokeLineJoin = strokeJoinRound,
                  strokeLineMiter = 4.0f,
                  pathFillType = NonZero,
              ) {
                moveTo(25.70f, 26.67f)
                arcToRelative(0.80f, 0.80f, 0.0f, false, true, -1.20f, -1.02f)
                arcToRelative(1.89f, 1.89f, 0.0f, false, false, -1.85f, 3.16f)
                arcToRelative(1.89f, 1.89f, 0.0f, false, false, 3.05f, -2.15f)
                moveTo(24.0f, 14.14f)
                lineToRelative(3.42f, -3.42f)
                arcToRelative(9.42f, 9.42f, 0.0f, true, true, 13.32f, 13.32f)
                lineToRelative(-0.75f, 0.75f)
                moveTo(10.68f, 27.46f)
                lineToRelative(-3.42f, -3.42f)
                arcToRelative(9.42f, 9.42f, 0.0f, true, true, 13.32f, -13.32f)
                lineToRelative(0.75f, 0.75f)
              }
            }
            .build()
    return _fanslyArcticons!!
  }

private var _fanslyArcticons: ImageVector? = null
