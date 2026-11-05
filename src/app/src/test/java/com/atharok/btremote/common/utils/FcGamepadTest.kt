// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

import com.atharok.btremote.domain.entities.remoteInput.*
import org.junit.Test
import org.junit.Assert.*

class FcGamepadTest {
    private fun p(name: String) = FcGamepadLayout.controls.single { it.label == name }.let { PadPoint(it.x,it.y) }
    @Test fun onlyFourButtonsAndOneDirectionPadWithBLeftOfA() {
        assertEquals(setOf(GamepadButton.A,GamepadButton.B,GamepadButton.SELECT,GamepadButton.START), FcGamepadLayout.controls.mapNotNull { it.button }.toSet())
        assertEquals(1,FcGamepadLayout.controls.count { it.kind == PadControlKind.DPAD })
        assertTrue(p("B").x<p("A").x)
    }
    @Test fun directionAndBothFaceButtonsRemainIndependentUntilReleased() {
        val t=GamepadTouchState(FcGamepadLayout.controls);val d=p("方向键")
        val held=mapOf(1L to PadPoint(d.x+50,d.y-50),2L to p("A"),3L to p("B"))
        val both=t.update(held);assertEquals(1,both.hat);assertEquals(GamepadButton.A.mask or GamepadButton.B.mask,both.buttons)
        val onlyB=t.update(held-2L);assertEquals(GamepadButton.B.mask,onlyB.buttons);assertEquals(1,onlyB.hat)
        val onlyDirection=t.update(held-2L-3L);assertEquals(0,onlyDirection.buttons);assertEquals(1,onlyDirection.hat)
        assertEquals(GamepadState(),t.clear());assertEquals(GamepadState(),t.update(emptyMap()))
    }
    @Test fun selectStartAreSeparateAndNeverMoveSticksOrTriggers() {
        val t=GamepadTouchState(FcGamepadLayout.controls)
        val s=t.update(mapOf(1L to p("SELECT"),2L to p("START")))
        assertEquals(GamepadState(buttons=GamepadButton.SELECT.mask or GamepadButton.START.mask),s)
        assertEquals(GamepadState(),t.update(emptyMap()))
    }
    @Test fun slidingBetweenFaceButtonsCannotPressOtherButtonAndAllDirectionsAreReachable() {
        val t=GamepadTouchState(FcGamepadLayout.controls);t.update(mapOf(1L to p("A")))
        assertEquals(0,t.update(mapOf(1L to p("B"))).buttons);t.clear()
        val d=p("方向键");val offsets=listOf(0 to -50,35 to -35,50 to 0,35 to 35,0 to 50,-35 to 35,-50 to 0,-35 to -35)
        offsets.forEachIndexed { i,(x,y)->assertEquals(i,t.update(mapOf(1L to PadPoint(d.x+x,d.y+y))).hat) }
        assertEquals(8,t.update(mapOf(1L to d)).hat)
    }
}
