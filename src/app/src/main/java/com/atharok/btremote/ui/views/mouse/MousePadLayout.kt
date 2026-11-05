package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.Ref
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.ui.views.ColoredMouseButtons
import com.atharok.btremote.ui.components.customButtons.EmptySurfaceButton
import com.atharok.btremote.ui.components.customButtons.IconHoldButton

@Composable
fun MousePadLayout(
    mouseSpeed: Float,
    scrollSpeed: Float,
    shouldInvertMouseScrollingDirection: Boolean,
    useGyroscope: Boolean,
    sendMouseInput: (MouseAction, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    coloredButtons: Boolean = false,
    integratedControls: Boolean = false
) {
    val mouseAction: Ref<MouseAction> = remember { Ref(MouseAction.NONE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val hasGyroscope = remember(context) {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }
    var airMouseEnabled by remember { mutableStateOf(false) }
    var touchActive by remember { mutableStateOf(false) }
    val currentSend by rememberUpdatedState(sendMouseInput)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                airMouseEnabled = false
                touchActive = false
                mouseAction.value = MouseAction.NONE
                sendMouseInput(MouseAction.NONE, 0f, 0f, 0f)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sendMouseInput(MouseAction.NONE, 0f, 0f, 0f)
        }
    }

    val gyroEnabled = if (integratedControls) airMouseEnabled else useGyroscope
    if (gyroEnabled && hasGyroscope && (!integratedControls || !touchActive)) {
        var firstSample by remember { mutableStateOf(true) }
        MouseGyroscope(mouseSpeed = mouseSpeed, onMousePositionChange = { x, y ->
            if (firstSample) firstSample = false
            else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                && (!integratedControls || (airMouseEnabled && !touchActive))) {
                currentSend(mouseAction.value, x, y, 0f)
            }
        })
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .weight(0.8f)
                .padding(bottom = dimensionResource(id = R.dimen.padding_min))
        ) {
            MousePad(
                mouseSpeed = mouseSpeed,
                scrollSpeed = scrollSpeed,
                onTouchActiveChanged = { touchActive = it },
                sendMouseAction = {
                    mouseAction.value = it
                    sendMouseInput(mouseAction.value, 0f, 0f, 0f)
                },
                sendMousePosition = { x: Float, y: Float ->
                    sendMouseInput(mouseAction.value, x, y, 0f)
                },
                sendMouseScroll = { wheel: Float ->
                    val mouseWheel = wheel * if(shouldInvertMouseScrollingDirection) -1f else 1f
                    sendMouseInput(mouseAction.value, 0f, 0f, mouseWheel)
                },
                shape = RoundedCornerShape(
                    topStart = dimensionResource(id = R.dimen.card_corner_radius),
                    topEnd = if (integratedControls) dimensionResource(id = R.dimen.card_corner_radius) else 0.dp,
                    bottomEnd = 0.dp,
                    bottomStart = 0.dp
                ),
                modifier = Modifier
                    .weight(if (integratedControls) 1f else 0.85f)
                    .padding(end = if (integratedControls) 0.dp else dimensionResource(id = R.dimen.padding_min))
            )

            if (!integratedControls) ScrollMouseButtonsLayout(
                scrollSpeed = scrollSpeed,
                sendMouseScroll = {
                    sendMouseInput(mouseAction.value, 0f, 0f, it)
                },
                modifier = Modifier
                    .weight(0.15f)
                    .padding(start = dimensionResource(id = R.dimen.padding_min))
            )
        }

        if (coloredButtons) ColoredMouseButtons(
            send = { mouseAction.value = it; sendMouseInput(it, 0f, 0f, 0f) },
            airMouseEnabled = if (integratedControls) airMouseEnabled else null,
            airMouseAvailable = hasGyroscope,
            onToggleAirMouse = {
                airMouseEnabled = !airMouseEnabled
                currentSend(mouseAction.value, 0f, 0f, 0f)
            },
            modifier = Modifier.fillMaxWidth().weight(0.2f).padding(top = dimensionResource(id = R.dimen.padding_min))
        ) else MouseButtonsLayout(
            sendMouseAction = {
                mouseAction.value = it
                sendMouseInput(mouseAction.value, 0f, 0f, 0f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f)
                .padding(top = dimensionResource(id = R.dimen.padding_min))
        )
    }
}

// ---- Mouse buttons ----

@Composable
private fun MouseButtonsLayout(
    sendMouseAction: (MouseAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Absolute.Left) {

        // Start
        EmptySurfaceButton(
            touchDown = { sendMouseAction(MouseAction.MOUSE_CLICK_LEFT) },
            touchUp = { sendMouseAction(MouseAction.NONE) },
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .absolutePadding(right = dimensionResource(id = R.dimen.padding_min)),
            shape = AbsoluteRoundedCornerShape(
                topLeft = 0.dp,
                topRight = 0.dp,
                bottomRight = 0.dp,
                bottomLeft = dimensionResource(id = R.dimen.card_corner_radius)
            )
        )

        // Center
        EmptySurfaceButton(
            touchDown = { sendMouseAction(MouseAction.MOUSE_CLICK_MIDDLE) },
            touchUp = { sendMouseAction(MouseAction.NONE) },
            modifier = Modifier
                .weight(0.24f)
                .fillMaxHeight()
                .padding(
                    horizontal = dimensionResource(id = R.dimen.padding_min)
                ),
            shape = RectangleShape
        )

        // End
        EmptySurfaceButton(
            touchDown = { sendMouseAction(MouseAction.MOUSE_CLICK_RIGHT) },
            touchUp = { sendMouseAction(MouseAction.NONE) },
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .absolutePadding(left = dimensionResource(id = R.dimen.padding_min)),
            shape = AbsoluteRoundedCornerShape(
                topLeft = 0.dp,
                topRight = 0.dp,
                bottomRight = dimensionResource(id = R.dimen.card_corner_radius),
                bottomLeft = 0.dp
            )
        )
    }
}

// ---- Scroll Up/Down buttons ----

@Composable
private fun ScrollMouseButtonsLayout(
    scrollSpeed: Float,
    sendMouseScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        IconHoldButton(
            image = AppIcons.MouseScrollUp,
            contentDescription = stringResource(id = R.string.mouse_wheel_up),
            onHold = {
                sendMouseScroll(1f)
            },
            onRelease = {
                sendMouseScroll(0f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(bottom = dimensionResource(id = R.dimen.padding_min)),
            durationBetweenRepeatsInMillis = getScrollDurationBetweenRepeats(scrollSpeed),
            shape = RoundedCornerShape(topEnd = dimensionResource(id = R.dimen.card_corner_radius))
        )

        IconHoldButton(
            image = AppIcons.MouseScrollDown,
            contentDescription = stringResource(id = R.string.mouse_wheel_down),
            onHold = {
                sendMouseScroll(-1f)
            },
            onRelease = {
                sendMouseScroll(0f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(top = dimensionResource(id = R.dimen.padding_min)),
            durationBetweenRepeatsInMillis = getScrollDurationBetweenRepeats(scrollSpeed),
            shape = RectangleShape
        )
    }
}

private fun getScrollDurationBetweenRepeats(speed: Float): Long = speed.takeIf { it > 0f }?.let { (100f / it).toLong().coerceIn(16L, 200L) } ?: 100L
