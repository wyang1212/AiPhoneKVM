package com.atharok.btremote.domain.entities.remoteInput

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelAccumulatorTest {
    @Test fun smallFingerMovementsAccumulateIntoAWheelStep() {
        val wheel = WheelAccumulator(20f)
        assertEquals(0, wheel.add(8f, 1f, false))
        assertEquals(0, wheel.add(8f, 1f, false))
        assertEquals(1, wheel.add(8f, 1f, false))
        assertEquals(1, wheel.add(16f, 1f, false))
    }

    @Test fun inverseDirectionAndFractionalSpeedAreRespected() {
        assertEquals(-2, WheelAccumulator(20f).add(80f, 0.5f, true))
        assertEquals(-1, WheelAccumulator(20f).add(-20f, 1f, false))
    }

    @Test fun liftingTheFingerDoesNotCarryDriftIntoANewGesture() {
        val wheel = WheelAccumulator(20f)
        wheel.add(19f, 1f, false)
        wheel.reset()
        assertEquals(0, wheel.add(2f, 1f, false))
    }

    @Test fun wheelPacketsStayInsideTheSignedHidByteRange() {
        assertEquals(127, WheelAccumulator(20f).add(4000f, 1f, false))
        assertEquals(-127, WheelAccumulator(20f).add(-4000f, 1f, false))
    }
}
