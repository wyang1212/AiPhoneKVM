// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.views

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atharok.btremote.common.utils.GamepadButton
import com.atharok.btremote.common.utils.GamepadReport
import com.atharok.btremote.common.utils.GamepadState
import com.atharok.btremote.domain.entities.remoteInput.*
import kotlin.math.min

@Composable
fun GamepadControlPanel(send: (ByteArray) -> Boolean, modifier: Modifier = Modifier, fc: Boolean = false) {
    val view=androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val previous=view.keepScreenOn
        view.keepScreenOn=true
        onDispose { view.keepScreenOn=previous }
    }
    val controls = if (fc) FcGamepadLayout.controls else GamepadLayout.controls
    val controller = remember(fc) { GamepadTouchState(controls) }
    var state by remember { mutableStateOf(GamepadState()) }
    var failed by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val latestSend by rememberUpdatedState(send)
    val owner = LocalLifecycleOwner.current
    fun reset() {
        state = controller.clear()
        latestSend(GamepadReport.neutral())
    }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) reset()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); reset() }
    }
    Column(modifier.background(if (fc) Color(0xffeee6d5) else Color(0xff171b24))) {
        if (failed) Text(
            "发送失败，请检查蓝牙连接",
            color = Color(0xffffb4ab), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Canvas(Modifier.fillMaxWidth().weight(1f)
            .semantics { contentDescription = if (fc) "FC 手柄操作区" else "Xbox 手柄操作区" }
            .onSizeChanged { canvasSize = it }
            .pointerInput(controller, canvasSize) {
                val scale = min(size.width / GamepadLayout.WIDTH, size.height / GamepadLayout.HEIGHT)
                if (scale <= 0) return@pointerInput
                val ox = (size.width - GamepadLayout.WIDTH * scale) / 2
                val oy = (size.height - GamepadLayout.HEIGHT * scale) / 2
                var last = GamepadReport.neutral()
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val touches = event.changes.filter { it.pressed }.associate {
                                it.id.value to PadPoint((it.position.x - ox) / scale, (it.position.y - oy) / scale)
                            }
                            state = controller.update(touches)
                            val report = GamepadReport.encode(state)
                            if (event.changes.any { it.previousPressed && !it.pressed } || !report.contentEquals(last)) {
                                failed = !latestSend(report)
                                if (!failed) last = report
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                } finally { reset() }
            }) {
            val scale = min(size.width / GamepadLayout.WIDTH, size.height / GamepadLayout.HEIGHT)
            val ox = (size.width - GamepadLayout.WIDTH * scale) / 2
            val oy = (size.height - GamepadLayout.HEIGHT * scale) / 2
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            withTransform({ translate(ox, oy); scale(scale, scale, Offset.Zero) }) {
                fun label(text: String, x: Float, y: Float, color: Int = 0xffdce5f5.toInt(), textSize: Float = 18f) {
                    paint.color = color; paint.textSize = textSize
                    drawContext.canvas.nativeCanvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint)
                }
                if (fc) {
                    drawRoundRect(Color(0xff8b323b), Offset(8f, 8f), Size(944f, 284f), CornerRadius(24f), style = Stroke(5f))
                    label("FC", 480f, 70f, 0xff8b323b.toInt(), 30f)
                }
                for (c in controls) {
                    val center = Offset(c.x, c.y)
                    val pressed = c.button?.let { state.buttons and it.mask != 0 } ?: false
                    val fill = if (pressed) Color(0xff436b9f) else if (fc && c.button in listOf(GamepadButton.A,GamepadButton.B)) Color(0xffa82f3b) else Color(0xff293241)
                    when (c.kind) {
                        PadControlKind.LEFT_STICK, PadControlKind.RIGHT_STICK -> {
                            drawCircle(Color(0xff0e131c), c.radius, center)
                            drawCircle(Color(0xff47566d), c.radius, center, style = Stroke(2f))
                            val left = c.kind == PadControlKind.LEFT_STICK
                            val position = center + Offset(if (left) state.leftX else state.rightX, if (left) state.leftY else state.rightY) * (c.radius * 0.48f)
                            drawCircle(Color(0xff39485e), c.radius * 0.58f, position)
                            drawCircle(Color(0xff637897), c.radius * 0.58f, position, style = Stroke(2f))
                            label(if (left) "L" else "R", position.x, position.y, textSize = 22f)
                        }
                        PadControlKind.DPAD -> {
                            drawCircle(Color(0xff0e131c), c.radius, center)
                            val r = c.radius * 0.86f; val thickness = r * 0.64f
                            drawRoundRect(Color(0xff354156), Offset(c.x - thickness / 2, c.y - r), Size(thickness, r * 2), CornerRadius(8f))
                            drawRoundRect(Color(0xff354156), Offset(c.x - r, c.y - thickness / 2), Size(r * 2, thickness), CornerRadius(8f))
                            val hat = state.hat
                            val marks = listOf(Triple("▲", 0, Offset(0f, -r * 0.65f)), Triple("▶", 2, Offset(r * 0.65f, 0f)), Triple("▼", 4, Offset(0f, r * 0.65f)), Triple("◀", 6, Offset(-r * 0.65f, 0f)))
                            marks.forEach { (text, direction, offset) ->
                                val active = hat in 0..7 && ((hat - direction + 8) % 8 in listOf(0, 1, 7))
                                label(text, c.x + offset.x, c.y + offset.y, if (active) 0xff8bbdff.toInt() else 0xffaab9cf.toInt(), 17f)
                            }
                        }
                        PadControlKind.BUTTON -> {
                            if (c.radius > 0) {
                                drawCircle(fill, c.radius, center)
                                drawCircle(Color(0xff51617b), c.radius, center, style = Stroke(2f))
                            } else {
                                val topLeft = Offset(c.x - c.width / 2, c.y - c.height / 2)
                                drawRoundRect(fill, topLeft, Size(c.width, c.height), CornerRadius(12f))
                                drawRoundRect(Color(0xff51617b), topLeft, Size(c.width, c.height), CornerRadius(12f), style = Stroke(2f))
                            }
                            val color = if (fc) 0xfff9eee0.toInt() else when (c.button) {
                                GamepadButton.A -> 0xff88d99d.toInt(); GamepadButton.B -> 0xffff8b91.toInt()
                                GamepadButton.X -> 0xff80b7ff.toInt(); GamepadButton.Y -> 0xffffd36b.toInt()
                                else -> 0xffdce5f5.toInt()
                            }
                            label(c.label, c.x, c.y, color, if (c.label.length > 3) 13f else 21f)
                        }
                    }
                }
            }
        }
    }
}
