// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticScreenControlTest {
    private val client="screen-session-123456"
    private val screen=ControlledScreen(720,1600,0,320)
    @Test fun geometryReportsWithoutAccessibilityAndExpires()=runTest {
        var now=100000L;val hub=CompanionControlHub {now}
        val poll=async {hub.next(client,false,screen.json())};runCurrent()
        assertFalse(hub.available());assertEquals(screen,hub.currentScreen())
        now+=35001;assertNull(hub.currentScreen());advanceUntilIdle();poll.await()
    }
    @Test fun commandCarriesExactAutomaticDisplaySnapshot()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true,screen.json())};runCurrent()
        val action=async {hub.execute("TAP",buildJsonObject {put("x",700);put("y",1500)})};runCurrent()
        val work=poll.await()!!
        assertEquals(screen.json(),work["params"]!!.jsonObject["screen"])
        assertTrue(hub.lease(work.stringValue("id"),client))
        hub.accept(buildJsonObject {put("id",work.stringValue("id"));put("session",client);put("status","SUCCESS")})
        action.await()
    }
    @Test fun rotationWhileModelIsThinkingRejectsOldCoordinates()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true,screen.json())};runCurrent()
        hub.updateScreen(screen.copy(width=1600,height=720,rotation=1))
        val result=runCatching {hub.execute("TAP",buildJsonObject {put("x",100);put("y",200);put("screen",screen.json())})}
        assertTrue(result.isFailure);assertTrue(result.exceptionOrNull()!!.message!!.contains("重新截图"))
        poll.cancelAndJoin()
    }
    @Test fun newCompanionSessionAndStopForgetPreviousDeviceScreen()=runTest {
        val hub=CompanionControlHub();val old=async {hub.next(client,false,screen.json())};runCurrent()
        val next=async {hub.next("another-session-123456",false)};runCurrent();assertNull(hub.currentScreen())
        hub.updateScreen(screen);hub.stop();assertNull(hub.currentScreen())
        advanceUntilIdle();old.await();next.await()
    }
}
