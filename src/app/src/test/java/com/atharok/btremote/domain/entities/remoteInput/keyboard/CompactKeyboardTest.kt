package com.atharok.btremote.domain.entities.remoteInput.keyboard

import org.junit.Assert.*
import org.junit.Test

class CompactKeyboardTest {
    @Test fun portraitHasAllLettersInPhoneRowsAndOnlyOneShift() {
        val rows = CompactKeyboard.rows(CompactKeyboardLayer.LETTERS)
        assertEquals("qwertyuiop", rows[0].mapNotNull { it.character }.joinToString(""))
        assertEquals("asdfghjkl", rows[1].mapNotNull { it.character }.joinToString(""))
        assertEquals("zxcvbnm", rows[2].mapNotNull { it.character }.joinToString(""))
        assertEquals(26, rows.flatten().mapNotNull { it.character }.count { it.isLetter() })
        assertEquals(1, rows.flatten().count { it.label == "Shift" })
        assertEquals(1, rows.flatten().count { it.usage == 0x2a })
        assertFalse(rows.flatten().any { it.label in listOf("Meta", "Alt Gr", "F1") })
    }

    @Test fun numbersAndSymbolsAreReachableWithoutSendingLiteralPageNames() {
        val digits = CompactKeyboard.rows(CompactKeyboardLayer.NUMBERS).flatten().mapNotNull { it.character }
        assertTrue(digits.containsAll(('0'..'9').toList()))
        assertTrue(CompactKeyboard.rows(CompactKeyboardLayer.NUMBERS).flatten().any { it.label == "#+=" && it.character == null })
        assertTrue(CompactKeyboard.rows(CompactKeyboardLayer.SYMBOLS).flatten().any { it.character == '\\' })
        CompactKeyboardLayer.entries.forEach { layer ->
            assertTrue(CompactKeyboard.rows(layer).flatten().all { it.character == null || it.character.code < 128 })
        }
    }

    @Test fun functionPageHasNoDuplicatedModifiers() {
        val keys = CompactKeyboard.rows(CompactKeyboardLayer.FUNCTIONS).flatten()
        for (label in listOf("Ctrl", "Alt", "Meta", "Tab", "Esc")) assertEquals(1, keys.count { it.label == label })
        assertEquals((0x3a..0x45).toList(), keys.filter { it.label.startsWith("F") }.map { it.usage })
    }

    @Test fun oneShotShiftUppercasesOnlyTheNextLetter() {
        val shifted = CompactModifiers().toggleShift()
        assertArrayEquals(byteArrayOf(2, 4), shifted.report(byteArrayOf(0, 4)))
        val next = shifted.afterKey(4)
        assertEquals(ShiftMode.OFF, next.shift)
        assertArrayEquals(byteArrayOf(0, 5), next.report(byteArrayOf(0, 5)))
    }

    @Test fun capsAndShortcutModifiersAreIndependent() {
        val caps = CompactModifiers().toggleShift().toggleShift()
        assertEquals(ShiftMode.LOCKED, caps.afterKey(4).shift)
        assertEquals(ShiftMode.OFF, caps.toggleShift().shift)
        val shortcut = CompactModifiers(control = true, alt = true)
        assertArrayEquals(byteArrayOf(5, 6), shortcut.report(byteArrayOf(0, 6)))
        assertArrayEquals(byteArrayOf(0, 7), shortcut.afterKey(6).report(byteArrayOf(0, 7)))
    }

    @Test fun symbolReportsKeepTheirExistingShiftBit() {
        assertArrayEquals(byteArrayOf(2, 0x1e), CompactModifiers().report(byteArrayOf(2, 0x1e)))
    }
}
