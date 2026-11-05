package com.atharok.btremote.ui.views.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardKey

object AndroidKeyMapper {
    private val inputs: Map<Key, Byte> = mapOf(
        Key.One to KeyboardKey.ROW_1_KEY_01.byte,
        Key.Two to KeyboardKey.ROW_1_KEY_02.byte,
        Key.Three to KeyboardKey.ROW_1_KEY_03.byte,
        Key.Four to KeyboardKey.ROW_1_KEY_04.byte,
        Key.Five to KeyboardKey.ROW_1_KEY_05.byte,
        Key.Six to KeyboardKey.ROW_1_KEY_06.byte,
        Key.Seven to KeyboardKey.ROW_1_KEY_07.byte,
        Key.Eight to KeyboardKey.ROW_1_KEY_08.byte,
        Key.Nine to KeyboardKey.ROW_1_KEY_09.byte,
        Key.Zero to KeyboardKey.ROW_1_KEY_10.byte,
        Key.Minus to KeyboardKey.ROW_1_KEY_11.byte,
        Key.Equals to KeyboardKey.ROW_1_KEY_12.byte,
        Key.Grave to KeyboardKey.ROW_1_KEY_00.byte,
        Key.Q to KeyboardKey.ROW_2_KEY_00.byte,
        Key.W to KeyboardKey.ROW_2_KEY_01.byte,
        Key.E to KeyboardKey.ROW_2_KEY_02.byte,
        Key.R to KeyboardKey.ROW_2_KEY_03.byte,
        Key.T to KeyboardKey.ROW_2_KEY_04.byte,
        Key.Y to KeyboardKey.ROW_2_KEY_05.byte,
        Key.U to KeyboardKey.ROW_2_KEY_06.byte,
        Key.I to KeyboardKey.ROW_2_KEY_07.byte,
        Key.O to KeyboardKey.ROW_2_KEY_08.byte,
        Key.P to KeyboardKey.ROW_2_KEY_09.byte,
        Key.LeftBracket to KeyboardKey.ROW_2_KEY_10.byte,
        Key.RightBracket to KeyboardKey.ROW_2_KEY_11.byte,
        Key.A to KeyboardKey.ROW_3_KEY_00.byte,
        Key.S to KeyboardKey.ROW_3_KEY_01.byte,
        Key.D to KeyboardKey.ROW_3_KEY_02.byte,
        Key.F to KeyboardKey.ROW_3_KEY_03.byte,
        Key.G to KeyboardKey.ROW_3_KEY_04.byte,
        Key.H to KeyboardKey.ROW_3_KEY_05.byte,
        Key.J to KeyboardKey.ROW_3_KEY_06.byte,
        Key.K to KeyboardKey.ROW_3_KEY_07.byte,
        Key.L to KeyboardKey.ROW_3_KEY_08.byte,
        Key.Semicolon to KeyboardKey.ROW_3_KEY_09.byte,
        Key.Apostrophe to KeyboardKey.ROW_3_KEY_10.byte,
        Key.Backslash to KeyboardKey.ROW_3_KEY_11.byte,
        Key.Z to KeyboardKey.ROW_4_KEY_01.byte,
        Key.X to KeyboardKey.ROW_4_KEY_02.byte,
        Key.C to KeyboardKey.ROW_4_KEY_03.byte,
        Key.V to KeyboardKey.ROW_4_KEY_04.byte,
        Key.B to KeyboardKey.ROW_4_KEY_05.byte,
        Key.N to KeyboardKey.ROW_4_KEY_06.byte,
        Key.M to KeyboardKey.ROW_4_KEY_07.byte,
        Key.Comma to KeyboardKey.ROW_4_KEY_08.byte,
        Key.Period to KeyboardKey.ROW_4_KEY_09.byte,
        Key.Slash to KeyboardKey.ROW_4_KEY_10.byte,
        Key.F1 to KeyboardKey.KEY_F1.byte,
        Key.F2 to KeyboardKey.KEY_F2.byte,
        Key.F3 to KeyboardKey.KEY_F3.byte,
        Key.F4 to KeyboardKey.KEY_F4.byte,
        Key.F5 to KeyboardKey.KEY_F5.byte,
        Key.F6 to KeyboardKey.KEY_F6.byte,
        Key.F7 to KeyboardKey.KEY_F7.byte,
        Key.F8 to KeyboardKey.KEY_F8.byte,
        Key.F9 to KeyboardKey.KEY_F9.byte,
        Key.F10 to KeyboardKey.KEY_F10.byte,
        Key.F11 to KeyboardKey.KEY_F11.byte,
        Key.F12 to KeyboardKey.KEY_F12.byte,
        Key.Enter to KeyboardKey.KEY_ENTER.byte,
        Key.Escape to KeyboardKey.KEY_ESCAPE.byte,
        Key.Backspace to KeyboardKey.KEY_DELETE.byte,
        Key.Tab to KeyboardKey.KEY_TAB.byte,
        Key.Spacebar to KeyboardKey.KEY_SPACE_BAR.byte,
        Key.DirectionRight to KeyboardKey.KEY_RIGHT_ARROW.byte,
        Key.DirectionLeft to KeyboardKey.KEY_LEFT_ARROW.byte,
        Key.DirectionDown to KeyboardKey.KEY_DOWN_ARROW.byte,
        Key.DirectionUp to KeyboardKey.KEY_UP_ARROW.byte,
        Key.PrintScreen to KeyboardKey.KEY_PRINT_SCREEN.byte,
    )

    fun getKeyboardReport(keyEvent: KeyEvent): ByteArray? {
        val key = inputs[keyEvent.key] ?: return null
        return byteArrayOf(keyEvent.modifierByte(), key)
    }

    private fun KeyEvent.modifierByte(): Byte {
        var value = 0
        if(isCtrlPressed) value = value or KeyboardKey.KEY_CTRL_LEFT.byte.toInt()
        if(isShiftPressed) value = value or KeyboardKey.KEY_SHIFT_LEFT.byte.toInt()
        if(isAltPressed) value = value or KeyboardKey.KEY_ALT.byte.toInt()
        if(isMetaPressed) value = value or KeyboardKey.KEY_META_LEFT.byte.toInt()
        return value.toByte()
    }
}
