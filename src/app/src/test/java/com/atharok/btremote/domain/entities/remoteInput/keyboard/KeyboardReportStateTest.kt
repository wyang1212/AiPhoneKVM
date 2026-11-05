package com.atharok.btremote.domain.entities.remoteInput.keyboard

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class KeyboardReportStateTest {
    @Test fun functionAndEscapeKeysUseTheKeyByte() {
        val keyboard = KeyboardReportState()
        assertArrayEquals(byteArrayOf(0, 0x3a), keyboard.press(0x3a, false))
        assertArrayEquals(byteArrayOf(0, 0), keyboard.release(0x3a, false))
        assertArrayEquals(byteArrayOf(0, 0x29), keyboard.press(0x29, false))
    }

    @Test fun releasingShiftKeepsControlAndTheLetterHeld() {
        val keyboard = KeyboardReportState()
        keyboard.press(0x01, true)
        keyboard.press(0x02, true)
        assertArrayEquals(byteArrayOf(0x03, 0x04), keyboard.press(0x04, false))
        assertArrayEquals(byteArrayOf(0x01, 0x04), keyboard.release(0x02, true))
        assertArrayEquals(byteArrayOf(0x01, 0), keyboard.release(0x04, false))
        assertArrayEquals(byteArrayOf(0, 0), keyboard.release(0x01, true))
    }

    @Test fun releasingAnEarlierLetterDoesNotReleaseTheLatestLetter() {
        val keyboard = KeyboardReportState()
        keyboard.press(0x04, false)
        keyboard.press(0x05, false)
        assertArrayEquals(byteArrayOf(0, 0x05), keyboard.release(0x04, false))
        assertArrayEquals(byteArrayOf(0, 0), keyboard.release(0x05, false))
    }

    @Test fun duplicateModifierPressDoesNotChangeItsBit() {
        val keyboard = KeyboardReportState()
        keyboard.press(0x02, true)
        assertArrayEquals(byteArrayOf(0x02, 0), keyboard.press(0x02, true))
        assertArrayEquals(byteArrayOf(0, 0), keyboard.release(0x02, true))
    }

    @Test fun returningFromBackgroundDoesNotKeepModifiers() {
        val keyboard = KeyboardReportState()
        keyboard.press(0x02, true)
        keyboard.press(0x04, false)
        assertArrayEquals(byteArrayOf(0, 0), keyboard.reset())
        assertArrayEquals(byteArrayOf(0, 0x05), keyboard.press(0x05, false))
    }
}
