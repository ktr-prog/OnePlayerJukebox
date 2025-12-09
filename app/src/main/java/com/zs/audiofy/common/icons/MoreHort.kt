package com.zs.audiofy.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Outlined
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round as strokeCapRound
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as strokeJoinRound
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val Icons.Outlined.MoreHort: ImageVector
    get() {
        if (_moreHort != null) {
            return _moreHort!!
        }
        _moreHort =
            Builder(
                    name = "MoreHort",
                    defaultWidth = 24.0.dp,
                    defaultHeight = 24.0.dp,
                    viewportWidth = 25.0f,
                    viewportHeight = 25.0f,
                )
                .apply {
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF000000)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(8.5f, 12.5f)
                        arcToRelative(2.0f, 2.0f, 0.0f, true, true, -4.0f, 0.0f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, true, 4.0f, 0.0f)
                        moveTo(14.5f, 12.5f)
                        arcToRelative(2.0f, 2.0f, 0.0f, true, true, -4.0f, 0.0f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, true, 4.0f, 0.0f)
                        moveTo(20.5f, 12.5f)
                        arcToRelative(2.0f, 2.0f, 0.0f, true, true, -4.0f, 0.0f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, true, 4.0f, 0.0f)
                    }
                }
                .build()
        return _moreHort!!
    }

@Suppress("ObjectPropertyName")
private var _moreHort: ImageVector? = null
