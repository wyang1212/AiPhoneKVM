package com.atharok.btremote.ui.components.customButtons

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atharok.btremote.common.extensions.autoMirroredIcon
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.theme.surfaceElevationShadow

// ---- Surface Button ----

@Composable
fun IconSurfaceButton(
    image: ImageVector,
    contentDescription: String,
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    elevation: Dp = surfaceElevationMedium(),
    shadowElevation: Dp = surfaceElevationShadow(),
    @FloatRange(from = 0.0, to = 1.0) iconFillFraction: Float = 0.5f,
    iconPadding: Dp = 0.dp,
    iconTint: Color = LocalContentColor.current
) {
    SurfaceButton(
        touchDown = touchDown,
        touchUp = touchUp,
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        shadowElevation = shadowElevation
    ) {
        Icon(
            imageVector = image,
            contentDescription = contentDescription,
            modifier = Modifier.autoMirroredIcon(image).fillMaxSize(iconFillFraction).padding(iconPadding),
            tint = iconTint
        )
    }
}

@Composable
fun RemoteIconSurfaceButton(
    properties: RemoteButtonProperties,
    sendKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    elevation: Dp = surfaceElevationMedium(),
    shadowElevation: Dp = surfaceElevationShadow(),
    @FloatRange(from = 0.0, to = 1.0) iconFillFraction: Float = 0.5f,
    iconPadding: Dp = 0.dp,
    iconTint: Color = LocalContentColor.current
) {
    IconSurfaceButton(
        image = properties.icon,
        contentDescription = stringResource(id = properties.stringRes),
        touchDown = { sendKeyReport(properties.input) },
        touchUp = { sendKeyReport(REMOTE_INPUT_NONE) },
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        shadowElevation = shadowElevation,
        iconFillFraction = iconFillFraction,
        iconPadding = iconPadding,
        iconTint = iconTint
    )
}

// ---- Raw Button ----

@Composable
private fun IconRawButton(
    image: ImageVector,
    contentDescription: String,
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    @FloatRange(from = 0.0, to = 1.0) iconFillFraction: Float = 0.5f,
    iconPadding: Dp = 0.dp,
    iconTint: Color = LocalContentColor.current
) {
    RawButton(
        touchDown = touchDown,
        touchUp = touchUp,
        modifier = modifier,
        shape = shape
    ) {
        Icon(
            imageVector = image,
            contentDescription = contentDescription,
            modifier = Modifier.autoMirroredIcon(image).fillMaxSize(iconFillFraction).padding(iconPadding),
            tint = iconTint
        )
    }
}

@Composable
fun RemoteIconRawButton(
    properties: RemoteButtonProperties,
    sendKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    @FloatRange(from = 0.0, to = 1.0) iconFillFraction: Float = 0.5f,
    iconTint: Color = LocalContentColor.current
) {
    IconRawButton(
        image = properties.icon,
        contentDescription = stringResource(id = properties.stringRes),
        touchDown = { sendKeyReport(properties.input) },
        touchUp = { sendKeyReport(REMOTE_INPUT_NONE) },
        modifier = modifier,
        shape = shape,
        iconFillFraction = iconFillFraction,
        iconTint = iconTint
    )
}