package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.atharok.btremote.common.utils.Ref
import com.atharok.btremote.common.utils.TAP_DRIFT_MAX_PX
import com.atharok.btremote.common.utils.TAP_DURATION_MAX
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.ui.components.DefaultElevatedCard

@Composable
fun MousePad(
    mouseSpeed: Float,
    scrollSpeed: Float,
    sendMouseAction: (input: MouseAction) -> Unit,
    sendMousePosition: (Float, Float) -> Unit,
    sendMouseScroll: (Float) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    onTouchActiveChanged: (Boolean) -> Unit = {}
) {
    val currentAction by rememberUpdatedState(sendMouseAction)
    val currentPosition by rememberUpdatedState(sendMousePosition)
    val currentScroll by rememberUpdatedState(sendMouseScroll)
    val currentActive by rememberUpdatedState(onTouchActiveChanged)
    val tapStartTimestamp = remember { Ref(0L) }
    val tapFingerCount = remember { Ref(0) }
    val tapAccumulatedDrift = remember { Ref(0f) }
    val scrollAccumulator = remember { Ref(0f) }

    DefaultElevatedCard(
        modifier = modifier,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mouseSpeed, scrollSpeed) {
                    awaitEachGesture {
                        awaitFirstDown()
                        tapStartTimestamp.value = System.currentTimeMillis()
                        tapFingerCount.value = 1
                        tapAccumulatedDrift.value = 0f
                        scrollAccumulator.value = 0f
                        currentActive(true)
                        try {
                            do {
                                val inputChanges = awaitPointerEvent().changes
                                // After a multi-finger gesture, lifting one finger must not move the pointer.
                                if (tapFingerCount.value == 1 && inputChanges.size == 1 && inputChanges.any { it.pressed }) {
                                    doMove(mouseSpeed, inputChanges, currentPosition)
                                }
                                doTap(
                                    inputChanges = inputChanges,
                                    tapStartTimestamp = tapStartTimestamp.value,
                                    onTapStartTimestampChange = { tapStartTimestamp.value = it },
                                    tapFingerCount = tapFingerCount.value,
                                    onTapFingerCountChange = { tapFingerCount.value = it },
                                    tapAccumulatedDrift = tapAccumulatedDrift.value,
                                    onTapAccumulatedDriftChange = { tapAccumulatedDrift.value = it },
                                    sendMouseAction = currentAction
                                )
                                doWheel(inputChanges.filter { it.pressed }, scrollSpeed, scrollAccumulator, currentScroll)
                                inputChanges.forEach { it.consume() }
                            } while (inputChanges.any { it.pressed })
                        } finally {
                            tapStartTimestamp.value = 0L
                            tapFingerCount.value = 0
                            tapAccumulatedDrift.value = 0f
                            scrollAccumulator.value = 0f
                            currentActive(false)
                            currentScroll(0f)
                        }
                    }
                }

        )
    }
}

// ---- Actions ----

private fun doMove(
    mouseSpeed: Float,
    inputChanges: List<PointerInputChange>,
    sendMousePosition: (Float, Float) -> Unit
) {
    if(inputChanges.size != 1) return
    val delta: Offset = inputChanges.first().positionChange()
    sendMousePosition(delta.x * mouseSpeed, delta.y * mouseSpeed)
}

private fun doTap(
    inputChanges: List<PointerInputChange>,
    tapStartTimestamp: Long,
    onTapStartTimestampChange: (Long) -> Unit,
    tapFingerCount: Int,
    onTapFingerCountChange: (Int) -> Unit,
    tapAccumulatedDrift: Float,
    onTapAccumulatedDriftChange: (Float) -> Unit,
    sendMouseAction: (input: MouseAction) -> Unit
) {
    // Store the maximum number of fingers that simultaneously touched the pad, used to determine
    // the tap action (1 finger = left click, 2 fingers = right click, 3 fingers = middle click).
    if (inputChanges.size > tapFingerCount) {
        onTapFingerCountChange(inputChanges.size)
    }

    // If a first finger touches the pad, store the timestamp of the initial contact.
    val isFingerDown = inputChanges.any { it.changedToDown() }
    if (isFingerDown && tapStartTimestamp == 0L) {
        onTapStartTimestampChange(System.currentTimeMillis())
        return
    }

    // Track how much the fingers have moved on the pad. If they moved too much, the gesture will
    // not be considered a tap.
    onTapAccumulatedDriftChange(
        tapAccumulatedDrift + inputChanges.maxOf { it.positionChange().getDistance() }
    )

    // If all fingers are lifted, check if the gesture qualifies as a tap
    val isAllFingersUp = inputChanges.none { it.pressed }
    if (isAllFingersUp) {
        val tapDuration = System.currentTimeMillis() - tapStartTimestamp

        if (tapDuration < TAP_DURATION_MAX && tapAccumulatedDrift < TAP_DRIFT_MAX_PX) {
            when (tapFingerCount) {
                1 -> sendMouseAction(MouseAction.MOUSE_CLICK_LEFT)
                2 -> sendMouseAction(MouseAction.MOUSE_CLICK_RIGHT)
                3 -> sendMouseAction(MouseAction.MOUSE_CLICK_MIDDLE)
            }
            sendMouseAction(MouseAction.NONE)
        }

        // Reset state
        onTapStartTimestampChange(0L)
        onTapFingerCountChange(0)
        onTapAccumulatedDriftChange(0f)
    }
}

private fun doWheel(
    inputChanges: List<PointerInputChange>,
    scrollSpeed: Float,
    scrollAccumulator: Ref<Float>,
    sendMouseScroll: (Float) -> Unit
) {
    if(inputChanges.size != 2) return

    // Average the delta Y across all fingers using positionChange() for consistency
    val avgDeltaY = inputChanges
        .map { it.positionChange().y }
        .average()
        .toFloat()

    if (avgDeltaY == 0f) return // Nothing to scroll

    // Accumulate movement until a full scroll step is reached
    scrollAccumulator.value += avgDeltaY

    val steps = (scrollAccumulator.value / 20f).toInt()
    if (steps != 0) {
        sendMouseScroll(steps * scrollSpeed)
        sendMouseScroll(0f)
        scrollAccumulator.value -= steps * 20f
    }
}