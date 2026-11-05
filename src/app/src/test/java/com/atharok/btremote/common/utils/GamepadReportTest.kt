// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

import com.atharok.btremote.domain.entities.remoteInput.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GamepadReportTest {
    @Test fun neutralHasNullHatAndNoHeldInputs() {
        assertArrayEquals(byteArrayOf(0, 0, 8, 0, 0, 0, 0, 0, 0), GamepadReport.neutral())
    }
    @Test fun cddButtonUsagesKeepAbxyAndShouldersDistinct() {
        assertEquals(listOf(1, 2, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15), GamepadButton.entries.map { it.usage })
        for (button in GamepadButton.entries) {
            val p = GamepadReport.encode(GamepadState(buttons = button.mask))
            assertEquals(button.mask, (p[0].toInt() and 255) or ((p[1].toInt() and 255) shl 8))
        }
    }
    @Test fun axesAreSignedClampedAndNonFiniteIsNeutral() {
        val p = GamepadReport.encode(GamepadState(leftX = -2f, leftY = 2f, rightX = Float.NaN, rightY = Float.POSITIVE_INFINITY))
        assertArrayEquals(byteArrayOf(-127, 127, 0, 0), p.copyOfRange(3, 7))
    }
    @Test fun triggerBytesAreUnsignedAndIndependent() {
        val p = GamepadReport.encode(GamepadState(leftTrigger = -1, rightTrigger = 999))
        assertEquals(0, p[7].toInt() and 255); assertEquals(255, p[8].toInt() and 255)
    }
    @Test fun descriptorReportSizeAndCollectionAreValid() {
        val descriptor = GamepadReport.descriptor.map { it.toInt() and 255 }
        assertEquals(listOf(5, 1, 9, 5, 161, 1, 133, 5), descriptor.take(8))
        var count = 0; var size = 0; var bits = 0; var depth = 0; var pos = 0
        while (pos < descriptor.size) {
            val prefix = descriptor[pos++]; val n = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
            var value = 0
            repeat(n) { value = value or (descriptor[pos++ ] shl (it * 8)) }
            when (prefix and 0xfc) {
                0x74 -> size = value; 0x94 -> count = value; 0x80 -> bits += size * count
                0xa0 -> depth++; 0xc0 -> depth--
            }
            assertTrue(depth >= 0)
        }
        assertEquals(0, depth); assertEquals(GamepadReport.SIZE * 8, bits)
    }
    @Test fun combinedDescriptorPreservesExistingFourReports() {
        assertTrue("Older host SDP length boundary", bluetoothHidDescriptor.size <= 248)
        for (id in 1..5) assertTrue(bluetoothHidDescriptor.toList().windowed(2).any { (a,b) -> a.toInt() and 255 == 0x85 && b.toInt() == id })
        assertArrayEquals(GamepadReport.descriptor, bluetoothHidDescriptor.takeLast(GamepadReport.descriptor.size).toByteArray())
        val neutral = ConsumerReportCompatibility.route(GamepadReport.ID, GamepadReport.neutral()).single()
        assertEquals(GamepadReport.ID, neutral.id); assertArrayEquals(GamepadReport.neutral(), neutral.bytes)
    }
    private fun control(name: String) = GamepadLayout.controls.single { it.label == name }
    private fun center(name: String) = control(name).let { PadPoint(it.x, it.y) }
    @Test fun threeFingersHoldStickFaceAndShoulderIndependently() {
        val t = GamepadTouchState(); val stick = control("左摇杆")
        t.update(mapOf(1L to center("左摇杆")))
        val held = mapOf(1L to PadPoint(stick.x + 72, stick.y), 2L to center("A"), 3L to center("RB"))
        val s = t.update(held)
        assertEquals(1f, s.leftX, 0.001f); assertEquals(GamepadButton.A.mask or GamepadButton.RB.mask, s.buttons)
        val after = t.update(held - 2L)
        assertEquals(GamepadButton.RB.mask, after.buttons); assertEquals(1f, after.leftX, 0.001f)
        assertEquals(GamepadState(), t.update(emptyMap()))
    }
    @Test fun secondFingerCannotStealStick() {
        val t = GamepadTouchState(); val c = center("左摇杆")
        t.update(mapOf(1L to c))
        assertEquals(0f, t.update(mapOf(1L to c, 2L to PadPoint(c.x + 50, c.y))).leftX, 0f)
        assertEquals(GamepadState(), t.update(mapOf(2L to PadPoint(c.x + 50, c.y))))
    }
    @Test fun duplicateButtonTouchesUseReferenceCounting() {
        val t = GamepadTouchState();val p = center("A")
        t.update(mapOf(1L to p, 2L to p))
        assertEquals(GamepadButton.A.mask, t.update(mapOf(2L to p)).buttons)
        assertEquals(0, t.update(emptyMap()).buttons)
    }
    @Test fun slidingOffButtonReleasesWithoutPressingAnother() {
        val t = GamepadTouchState();t.update(mapOf(1L to center("A")))
        assertEquals(0, t.update(mapOf(1L to center("B"))).buttons)
    }
    @Test fun dpadHasEightDirectionsAndNeutralCenter() {
        val t = GamepadTouchState();val p = center("方向键")
        val offsets = listOf(0 to -40, 28 to -28, 40 to 0, 28 to 28, 0 to 40, -28 to 28, -40 to 0, -28 to -28)
        t.update(mapOf(1L to p))
        offsets.forEachIndexed { i,(x,y) -> assertEquals(i,t.update(mapOf(1L to PadPoint(p.x + x, p.y + y))).hat) }
        assertEquals(8, t.update(mapOf(1L to p)).hat)
        assertEquals(8, t.update(mapOf(1L to PadPoint(p.x + 200,p.y))).hat)
    }
    @Test fun sticksHaveRadialDeadzoneAndRoundBoundary() {
        val t = GamepadTouchState();val p = center("左摇杆");t.update(mapOf(1L to p))
        assertEquals(0f,t.update(mapOf(1L to PadPoint(p.x+5,p.y+5))).leftX,0f)
        val s=t.update(mapOf(1L to PadPoint(p.x+500,p.y+500)))
        assertTrue(abs(s.leftX - 0.7071f)<0.001f);assertTrue(abs(s.leftY - 0.7071f)<0.001f)
    }
    @Test fun cancelResetsButtonsHatBothSticksAndTriggers() {
        val t=GamepadTouchState()
        t.update(mapOf(1L to center("LT"),2L to center("RT"),3L to center("L3")))
        assertEquals(255,t.state.leftTrigger);assertEquals(255,t.state.rightTrigger)
        assertEquals(GamepadState(),t.clear());assertEquals(GamepadState(),t.state)
        assertEquals(GamepadState(),t.update(emptyMap()))
    }
    @Test fun rightStickDoesNotChangeLeftStick() {
        val t=GamepadTouchState();val p=center("右摇杆");t.update(mapOf(1L to p))
        val s=t.update(mapOf(1L to PadPoint(p.x,p.y-62)))
        assertEquals(-1f,s.rightY,0.001f);assertEquals(0f,s.leftY,0f)
    }
}
