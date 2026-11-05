// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraCaptureRoutingTest {
    private fun frame()=CaptureHub.Frame(byteArrayOf(1,2,3),1,2,720,1600,source="camera",sourceRevision=5)
    @Test fun localCameraDoesNotPollCompanionAndPublishesExactFrame()=runTest {
        val hub=CaptureHub();var requests=0
        hub.localProvider={ requests++;frame() }
        val result=hub.capture()
        assertEquals(1,requests);assertEquals("camera",result.source);assertEquals(result,hub.lastFrame.value)
        assertEquals(0L,hub.companionSeen.value);assertFalse(hub.capturing)
    }
    @Test fun cameraClosureCancelsPendingObservationWithoutPublishingStaleFrame()=runTest {
        val hub=CaptureHub();hub.localProvider={ delay(10000);frame() }
        val capture=async { hub.capture() };runCurrent();assertTrue(hub.capturing)
        hub.cancelPending();runCurrent()
        assertTrue(capture.isCancelled);assertNull(hub.lastFrame.value);assertFalse(hub.capturing)
    }
    @Test fun rejectedSecondObservationCannotReplaceFirstOwner()=runTest {
        val hub=CaptureHub();hub.localProvider={ delay(100);frame() }
        val capture=async { hub.capture() };runCurrent()
        assertTrue(runCatching { hub.capture() }.isFailure)
        advanceTimeBy(100);runCurrent();assertEquals("camera",capture.await().source)
    }
    @Test fun providerFailureDoesNotLeaveCaptureLocked()=runTest {
        val hub=CaptureHub();hub.localProvider={ error("camera disconnected") }
        assertTrue(runCatching { hub.capture() }.isFailure);assertFalse(hub.capturing)
        hub.localProvider={ frame() };assertEquals("camera",hub.capture().source)
    }
    @Test fun switchingBackRestoresCompanionRequestPath()=runTest {
        val hub=CaptureHub();hub.localProvider={ frame() };hub.capture()
        hub.stop();hub.localProvider=null
        assertNull(hub.lastFrame.value)
        val next=async { hub.capture() };runCurrent()
        assertNotNull(hub.next())
        hub.cancelPending();runCurrent();assertTrue(next.isCancelled)
    }
}
