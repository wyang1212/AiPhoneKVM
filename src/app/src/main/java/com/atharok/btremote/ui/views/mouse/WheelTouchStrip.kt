package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.WheelAccumulator

@Composable
fun WheelTouchStrip(speed: Float, invert: Boolean, sendWheel: (Float) -> Unit,
    onActiveChanged: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pixelsPerStep = with(LocalDensity.current) { 20.dp.toPx() }
    val accumulator = remember(pixelsPerStep) { WheelAccumulator(pixelsPerStep) }
    val owner = LocalLifecycleOwner.current
    val currentSend by rememberUpdatedState(sendWheel)
    val currentActive by rememberUpdatedState(onActiveChanged)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                accumulator.reset(); currentActive(false); currentSend(0f)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            accumulator.reset(); currentActive(false); currentSend(0f)
        }
    }
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxSize().semantics { contentDescription = "滚轮滑动区" }
            .pointerInput(speed, invert, pixelsPerStep) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    accumulator.reset()
                    currentActive(true)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                val steps = accumulator.add(change.positionChange().y, speed, invert)
                                if (steps != 0) { currentSend(steps.toFloat()); currentSend(0f) }
                            }
                            change.consume()
                        } while (change.pressed)
                    } finally {
                        accumulator.reset(); currentActive(false); currentSend(0f)
                    }
                }
            }.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween) {
            Icon(AppIcons.MouseScrollUp, contentDescription = null)
            Text("滚动", style = MaterialTheme.typography.labelLarge)
            Icon(AppIcons.MouseScrollDown, contentDescription = null)
        }
    }
}
