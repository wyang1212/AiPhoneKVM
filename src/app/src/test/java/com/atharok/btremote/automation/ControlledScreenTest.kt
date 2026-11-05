// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ControlledScreenTest {
    private val portrait = ControlledScreen(720,1600,0,320,"7541N","ZTE")
    @Test fun helperMetadataRoundTripsWithoutChangingManualFallback() {
        val parsed = ControlledScreen.parse(portrait.json())
        assertEquals(portrait,parsed)
        val stored = AutomationConfig(screenWidth=1080,screenHeight=2400,sensitivity=1.7)
        assertEquals(720,parsed.applyTo(stored).screenWidth)
        assertEquals(1600,parsed.applyTo(stored).screenHeight)
        assertEquals(1.7,parsed.applyTo(stored).sensitivity,0.0)
        assertEquals(1080,stored.screenWidth)
    }
    @Test fun rotatedCoordinatesAreNotReusableEvenAtSameSize() {
        assertFalse(portrait.sameCoordinates(portrait.copy(rotation=2)))
        assertFalse(portrait.sameCoordinates(portrait.copy(width=1600,height=720,rotation=1)))
        assertFalse(portrait.sameCoordinates(null))
        assertTrue(portrait.sameCoordinates(portrait.copy(model="display renamed")))
    }
    @Test fun croppedOrScaledCaptureCannotAuthorizePixelCoordinates() {
        assertFalse(portrait.matchesImage(360,800))
        assertFalse(portrait.copy(fullDisplay=false).matchesImage(720,1600))
        assertFalse(portrait.sameCoordinates(portrait.copy(fullDisplay=false)))
        assertEquals(1080,portrait.copy(fullDisplay=false).applyTo(AutomationConfig()).screenWidth)
    }
    @Test fun invalidHelperGeometryIsRejected() {
        for ((key,value) in listOf("width" to -1,"height" to 9000,"rotation" to 4,"densityDpi" to 0)) {
            val invalid=buildJsonObject { portrait.json().forEach { (k,v)->put(k,v) }; put(key,value) }
            assertTrue("must reject $key",runCatching { ControlledScreen.parse(invalid) }.isFailure)
        }
    }
}
