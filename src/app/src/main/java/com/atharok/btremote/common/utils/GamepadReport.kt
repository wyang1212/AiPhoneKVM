// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

import kotlin.math.roundToInt

/** Android CDD 7.2.6: button usages are intentionally not consecutive for ABXY. */
enum class GamepadButton(val usage: Int) {
    A(1), B(2), X(4), Y(5), LB(7), RB(8), LT(9), RT(10),
    SELECT(11), START(12), HOME(13), L3(14), R3(15);
    val mask: Int get() = 1 shl (usage - 1)
}

data class GamepadState(
    val buttons: Int = 0, val hat: Int = 8,
    val leftX: Float = 0f, val leftY: Float = 0f,
    val rightX: Float = 0f, val rightY: Float = 0f,
    val leftTrigger: Int = 0, val rightTrigger: Int = 0
)

object GamepadReport {
    const val ID = 5
    const val SIZE = 9
    // Game pad collection; 16 buttons, 4-bit hat with null=8, X/Y/Z/Rz, Brake/Gas.
    // https://source.android.com/docs/compatibility/16/android-16-cdd#7_2_6_game_controller_support
    // Reuse unchanged HID globals; keep the composite descriptor below the 256-byte SDP boundary.
    val descriptor: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x05, 0xa1, 0x01, 0x85, ID,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x10, 0x81, 0x02,
        0x05, 0x01, 0x09, 0x39, 0x25, 0x07,
        0x46, 0x3b, 0x01, 0x65, 0x14,
        0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
        0x81, 0x03,
        0x65, 0x00, 0x45, 0x00,
        0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
        0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x04, 0x81, 0x02,
        0x05, 0x02, 0x09, 0xc5, 0x09, 0xc4,
        0x15, 0x00, 0x26, 0xff, 0x00, 0x95, 0x02, 0x81, 0x02, 0xc0
    ).map { it.toByte() }.toByteArray()

    fun encode(state: GamepadState): ByteArray {
        fun axis(v: Float) = ((if (v.isFinite()) v else 0f).coerceIn(-1f, 1f) * 127).roundToInt().toByte()
        return byteArrayOf(
            state.buttons.toByte(), (state.buttons ushr 8).toByte(),
            (if (state.hat in 0..7) state.hat else 8).toByte(),
            axis(state.leftX), axis(state.leftY), axis(state.rightX), axis(state.rightY),
            state.leftTrigger.coerceIn(0, 255).toByte(), state.rightTrigger.coerceIn(0, 255).toByte()
        )
    }

    fun neutral(): ByteArray = encode(GamepadState())
}
