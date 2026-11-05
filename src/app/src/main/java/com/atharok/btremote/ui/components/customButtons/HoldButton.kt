package com.atharok.btremote.ui.components.customButtons

import androidx.annotation.FloatRange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atharok.btremote.common.extensions.autoMirroredIcon
import com.atharok.btremote.ui.components.DefaultElevatedCard
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.theme.surfaceElevationShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun HoldButton(
    onHold: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    durationBetweenRepeatsInMillis: Long = 100L,
    shape: Shape = RectangleShape,
    elevation: Dp = surfaceElevationMedium(),
    shadowElevation: Dp = surfaceElevationShadow(),
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    DefaultElevatedCard(
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .clipToBounds()
                .clickable {}
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Wait for the first finger to touch the button
                        awaitFirstDown()

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        // Execute onHold continuously until the finger is lifted
                        val job = coroutineScope.launch {
                            while (true) {
                                onHold()
                                delay(durationBetweenRepeatsInMillis)
                            }
                        }

                        waitForUpOrCancellation()
                        job.cancel()
                        onRelease()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun IconHoldButton(
    image: ImageVector,
    contentDescription: String,
    onHold: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    durationBetweenRepeatsInMillis: Long = 100L,
    shape: Shape = RectangleShape,
    elevation: Dp = surfaceElevationMedium(),
    shadowElevation: Dp = surfaceElevationShadow(),
    @FloatRange(from = 0.0, to = 1.0) iconFillFraction: Float = 0.5f,
    iconPadding: Dp = 0.dp,
    iconTint: Color = LocalContentColor.current
) {
    HoldButton(
        onHold = onHold,
        onRelease = onRelease,
        modifier = modifier,
        durationBetweenRepeatsInMillis = durationBetweenRepeatsInMillis,
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