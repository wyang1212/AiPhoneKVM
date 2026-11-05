// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import org.junit.Assert.*
import org.junit.Test

class DisplayStabilityTest {
    @Test fun initialFrameWaitsForDisplayToSettle() {
        val state=DisplayStability();state.observe("720:1600:0:360",1000)
        assertEquals(1000L,state.remaining(1000));assertFalse(state.stable("720:1600:0:360",1999))
        assertTrue(state.stable("720:1600:0:360",2000))
    }
    @Test fun normalImageAndHeartbeatObservationsDoNotResetTheTimer() {
        val state=DisplayStability();state.observe("portrait",1000);state.observe("portrait",1800)
        assertEquals(200L,state.remaining(1800));assertTrue(state.stable("portrait",2000))
    }
    @Test fun RotationAndHalfTurnRestartQuietInterval() {
        val state=DisplayStability();state.observe("720:1600:0:360",1000)
        state.observe("720:1600:2:360",3000);assertFalse(state.stable("720:1600:0:360",5000))
        assertFalse(state.stable("720:1600:2:360",3999));assertTrue(state.stable("720:1600:2:360",4000))
    }
    @Test fun rapidChangesRequireTheLatestGeometryToSettle() {
        val state=DisplayStability();state.observe("portrait",1000);state.observe("landscape",1200)
        state.observe("portrait",1500);assertEquals(500L,state.remaining(2000))
        assertFalse(state.stable("landscape",3000));assertTrue(state.stable("portrait",2500))
    }
}
