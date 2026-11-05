// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalLoopTest {
    private fun next(screen: String = "screen") = GoalObservation(GoalDecision(GoalOutcome.CONTINUE,"看到测试按钮","按钮可点击",buildJsonObject { put("action","TAP") }), screen)
    private fun done() = GoalObservation(GoalDecision(GoalOutcome.COMPLETED,"已完成","画面明确显示完成",null), "done")
    private fun ok() = buildJsonObject { put("status","SUCCESS") }

    @Test fun observesAgainAfterEachActionAndOnlyThenCompletes() = runTest {
        val order=mutableListOf<String>();val histories=mutableListOf<String>();var n=0
        val loop=GoalLoop({ _,history -> histories+=history;order+="observe";if(n++<2)next("screen$n") else done() },{order+="execute";ok()}, {},{order+="drain"},settle={})
        loop.start(this,"完成两步");advanceUntilIdle()
        assertEquals(listOf("observe","execute","observe","execute","observe","drain"),order)
        assertEquals(GoalTaskStatus.COMPLETED,loop.state.value.status);assertEquals(2,loop.state.value.steps);assertEquals(3,loop.state.value.rounds)
        assertTrue(histories[0].isEmpty());assertTrue(histories[2].contains("第2步"))
    }
    @Test fun sentReportAloneNeverMeansCompleted() = runTest {
        val loop=GoalLoop({_,_->next()}, {ok()}, {},{},settle={})
        loop.start(this,"完成目标",GoalLimits(1,1));advanceUntilIdle()
        assertEquals(GoalTaskStatus.LIMIT_REACHED,loop.state.value.status);assertEquals(1,loop.state.value.steps);assertEquals(2,loop.state.value.rounds)
    }
    @Test fun lastAllowedActionStillGetsACompletionObservation() = runTest {
        var n=0;val loop=GoalLoop({_,_->if(n++==0)next() else done()}, {ok()}, {},{},settle={})
        loop.start(this,"目标",GoalLimits(1,1));advanceUntilIdle();assertEquals(GoalTaskStatus.COMPLETED,loop.state.value.status)
    }
    @Test fun repeatedUnchangedFrameAndActionStopsBeforeThirdClick() = runTest {
        var clicks=0;val loop=GoalLoop({_,_->next()}, {clicks++;ok()}, {},{},settle={})
        loop.start(this,"目标");advanceUntilIdle();assertEquals(2,clicks);assertEquals(GoalTaskStatus.BLOCKED,loop.state.value.status)
    }
    @Test fun modelBlockedNeverExecutes() = runTest {
        var executed=false;val loop=GoalLoop({_,_->GoalObservation(GoalDecision(GoalOutcome.BLOCKED,"登录页","需要登录",null),"login")}, {executed=true;ok()}, {},{},settle={})
        loop.start(this,"目标");advanceUntilIdle();assertFalse(executed);assertEquals(GoalTaskStatus.BLOCKED,loop.state.value.status)
    }
    @Test fun invalidModelResponseStopsWithoutExecuting() = runTest {
        var executed=false;val loop=GoalLoop({_,_->throw IllegalArgumentException("无效JSON")}, {executed=true;ok()}, {},{},settle={})
        loop.start(this,"目标");advanceUntilIdle();assertFalse(executed);assertEquals(GoalTaskStatus.FAILED,loop.state.value.status)
    }
    @Test fun failedHidReportStopsFurtherObservations() = runTest {
        var observations=0;val loop=GoalLoop({_,_->observations++;next()}, {buildJsonObject {put("status","FAILED")}}, {},{},settle={})
        loop.start(this,"目标");advanceUntilIdle();assertEquals(1,observations);assertEquals(GoalTaskStatus.FAILED,loop.state.value.status)
    }
    @Test fun preservesActualBackendFailureInsteadOfMisreportingEveryFailureAsBluetooth() = runTest {
        val loop=GoalLoop({_,_->next()}, {buildJsonObject {put("status","FAILED");put("error","当前输入框拒绝写入")}}, {},{},settle={})
        loop.start(this,"目标");advanceUntilIdle();assertTrue(loop.state.value.message.contains("当前输入框拒绝写入"))
    }
    @Test fun cancellationWaitsForDrainingAndBlocksRestartUntilDone() = runTest {
        val observing=CompletableDeferred<Unit>();val drainGate=CompletableDeferred<Unit>();var executed=false
        val loop=GoalLoop({_,_->observing.complete(Unit);awaitCancellation()}, {executed=true;ok()}, {},{drainGate.await()},settle={})
        loop.start(this,"目标");runCurrent();assertTrue(observing.isCompleted)
        val stopped=async {loop.stopAndAwait()};runCurrent()
        assertTrue(loop.active);assertEquals(GoalTaskStatus.STOPPING,loop.state.value.status);assertFalse(stopped.isCompleted)
        assertThrows(IllegalStateException::class.java) {loop.start(this,"新目标")}
        drainGate.complete(Unit);advanceUntilIdle();assertTrue(stopped.isCompleted);assertFalse(executed);assertFalse(loop.active)
        assertEquals(GoalTaskStatus.CANCELLED,loop.state.value.status)
    }
    @Test fun immediateCancellationStillRunsCleanup() = runTest {
        var drained=false;val loop=GoalLoop({_,_->next()}, {ok()}, {},{drained=true},settle={})
        loop.start(this,"目标");loop.cancel();advanceUntilIdle();assertTrue(drained);assertFalse(loop.active);assertEquals(GoalTaskStatus.CANCELLED,loop.state.value.status)
    }
    @Test fun serviceScopeCancellationStopsLoopAndReleases() = runTest {
        val service=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));var drained=false
        val loop=GoalLoop({_,_->awaitCancellation()}, {ok()}, {},{drained=true},settle={})
        loop.start(service,"目标");runCurrent();service.cancel();advanceUntilIdle();assertTrue(drained);assertEquals(GoalTaskStatus.CANCELLED,loop.state.value.status)
    }
    @Test fun timeLimitUsesWholeTaskDeadline() = runTest {
        val loop=GoalLoop({_,_->delay(120_000);done()}, {ok()}, {},{},settle={})
        loop.start(this,"目标",GoalLimits(30,1));advanceUntilIdle();assertEquals(GoalTaskStatus.LIMIT_REACHED,loop.state.value.status)
    }
    @Test fun captureTimeoutIsAnErrorRatherThanWholeTaskTimeLimit() = runTest {
        val loop=GoalLoop({_,_->withTimeout(25_000){delay(60_000);done()}}, {ok()}, {},{},settle={})
        loop.start(this,"目标");advanceUntilIdle();assertEquals(GoalTaskStatus.FAILED,loop.state.value.status)
    }
    @Test fun completedAndStoppedTasksCanRestart() = runTest {
        val loop=GoalLoop({_,_->done()}, {ok()}, {},{},settle={})
        repeat(2){loop.start(this,"目标$it");advanceUntilIdle();assertEquals(GoalTaskStatus.COMPLETED,loop.state.value.status)}
        assertEquals("目标1",loop.state.value.goal);assertEquals(0,loop.state.value.steps)
    }
    @Test fun rejectsBlankGoalAndInvalidLimits() = runTest {
        val loop=GoalLoop({_,_->done()}, {ok()}, {},{},settle={})
        assertThrows(IllegalArgumentException::class.java){loop.start(this,"")}
        assertThrows(IllegalArgumentException::class.java){loop.start(this,"目标",GoalLimits(0,15))}
        assertThrows(IllegalArgumentException::class.java){loop.start(this,"目标",GoalLimits(30,61))}
    }
    @Test fun resetClearsPreviousDeviceGoalAfterStopped() = runTest {
        val loop=GoalLoop({_,_->done()}, {ok()}, {},{},settle={})
        loop.start(this,"上台设备的目标");advanceUntilIdle();loop.reset()
        assertEquals(GoalTaskStatus.IDLE,loop.state.value.status);assertEquals("",loop.state.value.goal);assertEquals("",loop.state.value.summary)
    }
    @Test fun opticalSensorNoiseStillStopsRepeatedIneffectiveClicks()=runTest {
        var rounds=0;var clicks=0
        val loop=GoalLoop({_,_->
            val n=rounds++
            val noisy=CameraSceneFingerprint.encode(List(64) { if(it<8 && n%2==0) 127 else 129 })
            next(noisy)
        },{clicks++;ok()},{},{},settle={})
        loop.start(this,"完成目标");advanceUntilIdle()
        assertEquals(2,clicks);assertEquals(GoalTaskStatus.BLOCKED,loop.state.value.status)
    }
}
