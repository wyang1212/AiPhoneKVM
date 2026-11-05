// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionControlHubTest {
    private val client="session-0000000001"
    private val params=buildJsonObject {put("x",20);put("y",30)}
    private fun answer(id: String,session: String=client)=buildJsonObject {put("id",id);put("session",session);put("status","SUCCESS")}
    @Test fun unavailableCannotEnqueue()=runTest {
        val hub=CompanionControlHub();assertTrue(runCatching {hub.execute("TAP",params)}.isFailure)
    }
    @Test fun requiresLeaseAndMatchingCompanionAndAcceptsOnlyOnce()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true)};runCurrent()
        val command=async {hub.execute("TAP",params)};runCurrent();val id=poll.await()!!.stringValue("id")
        assertFalse(hub.accept(answer(id)));assertFalse(hub.lease(id,"session-0000000002"))
        assertTrue(hub.lease(id,client));assertFalse(hub.lease(id,client));assertFalse(hub.accept(answer(id,"session-0000000002")))
        assertTrue(hub.accept(answer(id)));assertFalse(hub.accept(answer(id)));runCurrent()
        assertEquals("companion_accessibility",command.await().stringValue("delivery"))
    }
    @Test fun cancelBeforeLeasePreventsAnyStartAndAllowsNextCommand()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true)};runCurrent()
        val command=async {hub.execute("TAP",params)};runCurrent();val id=poll.await()!!.stringValue("id")
        command.cancelAndJoin();assertFalse(hub.lease(id,client));assertFalse(hub.accept(answer(id)))
        val nextPoll=async {hub.next(client,true)};val next=async {hub.execute("TAP",params)};runCurrent();val nextId=nextPoll.await()!!.stringValue("id")
        assertNotEquals(id,nextId);assertTrue(hub.lease(nextId,client));hub.accept(answer(nextId));assertEquals("companion_accessibility",next.await().stringValue("delivery"))
    }
    @Test fun cancelLeasedCommandWaitsForAcknowledgement()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true)};runCurrent()
        val command=async {hub.execute("TAP",params)};runCurrent();val id=poll.await()!!.stringValue("id");assertTrue(hub.lease(id,client))
        command.cancel();runCurrent();assertFalse(command.isCompleted)
        advanceTimeBy(1000);assertTrue(hub.accept(answer(id)));runCurrent();assertTrue(command.isCompleted)
    }
    @Test fun lostAckDrainsBoundedGestureAndDoesNotReplay()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true)};runCurrent()
        val command=async {hub.execute("SWIPE",params)};runCurrent();val id=poll.await()!!.stringValue("id");hub.lease(id,client)
        command.cancel();runCurrent();advanceTimeBy(3499);runCurrent();assertFalse(command.isCompleted)
        advanceTimeBy(2);runCurrent();assertTrue(command.isCompleted);assertFalse(hub.lease(id,client));assertFalse(hub.accept(answer(id)))
    }
    @Test fun permissionRevokedCancelsUnstartedCommand()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true)};runCurrent()
        val command=async {runCatching {hub.execute("TAP",params)}};runCurrent();val id=poll.await()!!.stringValue("id")
        val disabled=async {hub.next(client,false)};runCurrent();assertFalse(hub.available());assertFalse(hub.lease(id,client));assertTrue(command.await().isFailure);advanceUntilIdle();disabled.await()
    }
    @Test fun stoppedHubRejectsLateStart()=runTest {
        val hub=CompanionControlHub();val poll=async {hub.next(client,true)};runCurrent()
        val command=async {runCatching {hub.execute("TAP",params)}};runCurrent();val id=poll.await()!!.stringValue("id")
        hub.stop();assertFalse(hub.available());assertFalse(hub.lease(id,client));assertTrue(command.await().isFailure)
    }
    @Test fun expiredHeartbeatCannotExecute()=runTest {
        var clock=100000L;val hub=CompanionControlHub {clock};val poll=async {hub.next(client,true)};runCurrent();assertTrue(hub.available())
        clock+=35001;assertFalse(hub.available());assertTrue(runCatching {hub.execute("TAP",params)}.isFailure);poll.cancelAndJoin()
    }
}
