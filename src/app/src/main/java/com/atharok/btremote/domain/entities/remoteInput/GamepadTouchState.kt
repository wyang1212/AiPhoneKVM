// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.domain.entities.remoteInput

import com.atharok.btremote.common.utils.GamepadButton
import com.atharok.btremote.common.utils.GamepadState
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class PadPoint(val x: Float, val y: Float)
enum class PadControlKind { BUTTON, LEFT_STICK, RIGHT_STICK, DPAD }
data class PadControl(
    val label: String, val x: Float, val y: Float, val radius: Float,
    val button: GamepadButton? = null, val kind: PadControlKind = PadControlKind.BUTTON,
    val width: Float = radius * 2, val height: Float = radius * 2
) {
    fun contains(p: PadPoint): Boolean = if (radius > 0) hypot(p.x - x, p.y - y) <= radius
        else kotlin.math.abs(p.x - x) <= width / 2 && kotlin.math.abs(p.y - y) <= height / 2
}

/** Fixed Xbox-style geometry, scaled uniformly so hit areas and rendering stay identical. */
object GamepadLayout {
    const val WIDTH = 960f
    const val HEIGHT = 300f
    val controls = listOf(
        PadControl("LT", 96f, 26f, 0f, GamepadButton.LT, width = 112f, height = 48f),
        PadControl("LB", 96f, 78f, 0f, GamepadButton.LB, width = 112f, height = 48f),
        PadControl("RT", 864f, 26f, 0f, GamepadButton.RT, width = 112f, height = 48f),
        PadControl("RB", 864f, 78f, 0f, GamepadButton.RB, width = 112f, height = 48f),
        PadControl("左摇杆", 210f, 154f, 68f, kind = PadControlKind.LEFT_STICK),
        PadControl("方向键", 350f, 218f, 57f, kind = PadControlKind.DPAD),
        PadControl("右摇杆", 610f, 218f, 62f, kind = PadControlKind.RIGHT_STICK),
        PadControl("Y", 770f, 116f, 29f, GamepadButton.Y),
        PadControl("B", 826f, 163f, 29f, GamepadButton.B),
        PadControl("A", 770f, 210f, 29f, GamepadButton.A),
        PadControl("X", 714f, 163f, 29f, GamepadButton.X),
        PadControl("SELECT", 426f, 105f, 0f, GamepadButton.SELECT, width = 80f, height = 48f),
        PadControl("START", 534f, 105f, 0f, GamepadButton.START, width = 80f, height = 48f),
        PadControl("HOME", 480f, 173f, 25f, GamepadButton.HOME),
        PadControl("L3", 210f, 274f, 0f, GamepadButton.L3, width = 84f, height = 48f),
        PadControl("R3", 790f, 274f, 0f, GamepadButton.R3, width = 84f, height = 48f)
    )
}

/** Simple Famicom geometry: direction pad, SELECT/START, then B and A. */
object FcGamepadLayout {
    val controls = listOf(
        PadControl("方向键", 205f, 158f, 88f, kind = PadControlKind.DPAD),
        PadControl("SELECT", 420f, 205f, 0f, GamepadButton.SELECT, width = 100f, height = 52f),
        PadControl("START", 548f, 205f, 0f, GamepadButton.START, width = 100f, height = 52f),
        PadControl("B", 730f, 180f, 48f, GamepadButton.B),
        PadControl("A", 852f, 150f, 48f, GamepadButton.A)
    )
}

/** Each finger owns its initial control until lifted; one finger never steals a held stick. */
class GamepadTouchState(private val controls: List<PadControl> = GamepadLayout.controls) {
    private val owners = mutableMapOf<Long, PadControl?>()
    var state: GamepadState = GamepadState()
        private set

    fun clear(): GamepadState {
        owners.clear()
        state = GamepadState()
        return state
    }

    fun update(touches: Map<Long, PadPoint>): GamepadState {
        owners.keys.retainAll(touches.keys)
        for ((id, point) in touches) {
            if (!owners.containsKey(id)) {
                val control = controls.firstOrNull { it.contains(point) }
                owners[id] = if (control?.kind != PadControlKind.BUTTON && owners.values.contains(control)) null else control
            }
        }
        var mask = 0
        var hat = 8
        var left = PadPoint(0f, 0f)
        var right = PadPoint(0f, 0f)
        for ((id, control) in owners) {
            control ?: continue
            val point = touches.getValue(id)
            when (control.kind) {
                PadControlKind.BUTTON -> if (control.contains(point)) mask = mask or control.button!!.mask
                PadControlKind.LEFT_STICK -> left = stick(point, control)
                PadControlKind.RIGHT_STICK -> right = stick(point, control)
                PadControlKind.DPAD -> {
                    val dx = point.x - control.x; val dy = point.y - control.y
                    val distance = hypot(dx, dy)
                    if (distance > control.radius * 0.18f && distance <= control.radius * 1.65f) {
                        hat = ((atan2(dx.toDouble(), -dy.toDouble()) / (Math.PI / 4)).roundToInt() + 8) % 8
                    }
                }
            }
        }
        state = GamepadState(mask, hat, left.x, left.y, right.x, right.y,
            if (mask and GamepadButton.LT.mask != 0) 255 else 0,
            if (mask and GamepadButton.RT.mask != 0) 255 else 0)
        return state
    }

    private fun stick(point: PadPoint, control: PadControl): PadPoint {
        val dx = (point.x - control.x) / control.radius; val dy = (point.y - control.y) / control.radius
        val distance = hypot(dx, dy)
        if (distance <= 0.12f) return PadPoint(0f, 0f)
        val magnitude = ((distance.coerceAtMost(1f) - 0.12f) / 0.88f)
        return PadPoint(dx / distance * magnitude, dy / distance * magnitude)
    }
}
