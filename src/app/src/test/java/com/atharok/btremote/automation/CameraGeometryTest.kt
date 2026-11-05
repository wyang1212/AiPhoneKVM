// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import org.junit.Assert.*
import org.junit.Test

class CameraGeometryTest {
    private fun p(x:Float,y:Float)=CameraPoint(x,y)
    private val rectangle=listOf(p(.1f,.1f),p(.9f,.1f),p(.9f,.9f),p(.1f,.9f))
    @Test fun acceptsClockwisePerspectiveScreen() {
        CameraQuad(listOf(p(.2f,.1f),p(.85f,.2f),p(.9f,.9f),p(.1f,.8f))).validate()
    }
    @Test fun rejectsCrossedCorners() { assertTrue(runCatching { CameraQuad(listOf(rectangle[0],rectangle[2],rectangle[1],rectangle[3])).validate() }.isFailure) }
    @Test fun rejectsCounterclockwiseAndConcaveScreens() {
        assertTrue(runCatching { CameraQuad(rectangle.reversed()).validate() }.isFailure)
        assertTrue(runCatching { CameraQuad(listOf(p(.1f,.1f),p(.9f,.1f),p(.3f,.3f),p(.1f,.9f))).validate() }.isFailure)
    }
    @Test fun rejectsTooSmallAndNonfiniteScreen() {
        assertTrue(runCatching { CameraQuad(rectangle.map { p(it.x/10,it.y/10) }).validate() }.isFailure)
        assertTrue(runCatching { CameraQuad(listOf(p(Float.NaN,0f))+rectangle.take(3)).validate() }.isFailure)
    }
    @Test fun solvesIndependentAxisGainAndOffset() {
        val map=CameraPointerMap(p(.01f,.02f),p(.21f,.02f),p(.01f,.12f)).validate()
        assertEquals(400 to 800,map.units(p(.51f,.52f)))
        assertEquals(0 to 0,map.units(p(.01f,.02f)))
    }
    @Test fun solvesSmallAxisSkewWithoutGuessingScreenPixels() {
        val map=CameraPointerMap(p(.01f,.01f),p(.21f,.02f),p(.02f,.21f)).validate()
        assertEquals(320 to 480,map.units(p(.44f,.63f)))
    }
    @Test fun rejectsClippedOrWronglySelectedProbe() {
        assertTrue(runCatching { CameraPointerMap(p(.4f,.4f),p(.6f,.4f),p(.4f,.6f)).validate() }.isFailure)
        assertTrue(runCatching { CameraPointerMap(p(.01f,.01f),p(.011f,.01f),p(.01f,.2f)).validate() }.isFailure)
        assertTrue(runCatching { CameraPointerMap(p(.01f,.01f),p(.2f,.3f),p(.01f,.2f)).validate() }.isFailure)
    }
    @Test fun interiorMeasurementsRecoverHiddenRoundedCornerOrigin() {
        val map=CameraPointerMap.fromInterior(p(.11f,.06f),p(.21f,.06f),p(.11f,.11f))
        assertEquals(800 to 1600,map.units(p(.51f,.51f)))
        assertEquals(.01f,map.origin.x,.0001f)
        assertEquals(.01f,map.origin.y,.0001f)
        val slightCrop=CameraPointerMap.fromInterior(p(.09f,.04f),p(.19f,.04f),p(.09f,.09f))
        assertEquals(816 to 1632,slightCrop.units(p(.5f,.5f)))
    }
    @Test fun interiorMeasurementsRejectInvisibleOrInvalidProbes() {
        assertTrue(runCatching { CameraPointerMap.fromInterior(p(-.01f,.1f),p(.2f,.1f),p(.1f,.2f)) }.isFailure)
        assertTrue(runCatching { CameraPointerMap.fromInterior(p(.1f,.1f),p(.11f,.1f),p(.1f,.2f)) }.isFailure)
        assertTrue(runCatching { CameraPointerMap.fromInterior(p(.2f,.2f),p(.5f,.2f),p(.2f,.5f)) }.isFailure)
    }
    @Test fun screenVerificationDoesNotAcceptLargeError() {
        assertTrue(CameraPointerMap.accurate(p(.51f,.49f),p(.5f,.5f)))
        assertFalse(CameraPointerMap.accurate(p(.55f,.5f),p(.5f,.5f)))
    }
    @Test fun rejectsOutOfScreenTargetsBeforeMotion() {
        val map=CameraPointerMap(p(.01f,.01f),p(.21f,.01f),p(.01f,.21f)).validate()
        assertTrue(runCatching { map.units(p(-.1f,.2f)) }.isFailure)
    }
    @Test fun cameraNoiseDoesNotEvadeRepeatedActionGuard() {
        val a=CameraSceneFingerprint.encode(List(64) { 128 })
        val b=CameraSceneFingerprint.encode(List(64) { if(it<8) 127 else 129 })
        assertTrue(CameraSceneFingerprint.similarOrSame(a,b))
        assertFalse(CameraSceneFingerprint.similarOrSame(a,CameraSceneFingerprint.encode(List(64) { 40 })))
        assertTrue(CameraSceneFingerprint.similarOrSame("old-hash","old-hash"))
        assertFalse(CameraSceneFingerprint.similarOrSame("old-hash","different-hash"))
    }
    @Test fun distantCameraScreenIsRejectedBeforeCalibration() {
        val q=CameraQuad(rectangle).validate()
        assertTrue(q.sufficientlyDetailed(1200,1600,false))
        assertFalse(q.sufficientlyDetailed(240,400,false))
    }
}
