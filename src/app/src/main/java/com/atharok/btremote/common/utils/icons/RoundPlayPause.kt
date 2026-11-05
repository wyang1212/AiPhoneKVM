package com.atharok.btremote.common.utils.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RoundPlayPause: ImageVector
    get() {
        if (_RoundPlayPause != null) {
            return _RoundPlayPause!!
        }
        _RoundPlayPause = ImageVector.Builder(
            name = "RoundPlayPause",
            defaultWidth = 40.dp,
            defaultHeight = 24.dp,
            viewportWidth = 40f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveToRelative(4f, 6.82f)
                verticalLineToRelative(10.36f)
                curveToRelative(0f, 0.79f, 0.87f, 1.27f, 1.54f, 0.84f)
                lineToRelative(8.14f, -5.18f)
                curveToRelative(0.62f, -0.39f, 0.62f, -1.29f, 0f, -1.69f)
                lineTo(5.54f, 5.98f)
                curveTo(4.87f, 5.55f, 4f, 6.03f, 4f, 6.82f)
                close()
            }
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveToRelative(26f, 19f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveTo(28f, 5.9f, 27.1f, 5f, 26f, 5f)
                curveTo(24.9f, 5f, 24f, 5.9f, 24f, 7f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                close()
                moveTo(32f, 7f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveTo(36f, 5.9f, 35.1f, 5f, 34f, 5f)
                curveTo(32.9f, 5f, 32f, 5.9f, 32f, 7f)
                close()
            }
        }.build()

        return _RoundPlayPause!!
    }

@Suppress("ObjectPropertyName")
private var _RoundPlayPause: ImageVector? = null
