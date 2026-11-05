// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteGoalCommandsTest {
    private fun command(action: String = "AI_START", id: String = UUID.randomUUID().toString(), goal: String = "查看版本", expires: Long = 21000, task: String = UUID.randomUUID().toString()) = buildJsonObject {
        put("version", "1.0"); put("id", id); put("action", action)
        put("params", buildJsonObject { put("goal", goal); put("expiresAt", expires); put("taskId", task) })
    }.toString()
    private fun state() = buildJsonObject { put("status", "running") }

    @Test fun duplicateStartCannotRunAgainEvenAfterTaskEnds() = runTest {
        var starts = 0
        val handler = RemoteGoalCommands({ starts++ }, {}, { state() }, { 1000 })
        val raw = command()
        assertEquals("SUCCESS", handler.submit(raw).stringValue("status"))
        assertEquals(handler.submit(raw), handler.submit(raw)); assertEquals(1, starts)
    }
    @Test fun rejectedBusyStartStaysRejectedOnRedelivery() = runTest {
        var busy = true; var starts = 0
        val handler = RemoteGoalCommands({ check(!busy) { "正在执行" }; starts++ }, {}, { state() }, { 1000 })
        val raw = command(); assertEquals("FAILED", handler.submit(raw).stringValue("status"))
        busy = false
        assertEquals("FAILED", handler.submit(raw).stringValue("status")); assertEquals(0, starts)
    }
    @Test fun expiredOrDistantStartNeverRuns() = runTest {
        var starts = 0; val handler = RemoteGoalCommands({ starts++ }, {}, { state() }, { 1000 })
        for (time in listOf(0L, 1000L, 31001L)) assertEquals("FAILED", handler.submit(command(expires = time)).stringValue("status"))
        assertEquals(0, starts)
    }
    @Test fun sameIdCannotBeReusedForDifferentGoal() = runTest {
        var starts = 0; val handler = RemoteGoalCommands({ starts++ }, {}, { state() }, { 1000 }); val id = UUID.randomUUID().toString()
        handler.submit(command(id = id))
        assertEquals("FAILED", handler.submit(command(id = id, goal = "其他目标")).stringValue("status")); assertEquals(1, starts)
    }
    @Test fun stopTargetsOneTaskAndWaitsForDrain() = runTest {
        var stopped = ""; var drained = false
        val handler = RemoteGoalCommands({}, { stopped = it; delay(50); drained = true }, { state() }, { 1000 })
        val task = UUID.randomUUID().toString()
        val result = async { handler.submit(command(action = "AI_STOP", task = task)) }
        runCurrent(); assertFalse(result.isCompleted); advanceUntilIdle()
        assertTrue(drained); assertEquals(task, stopped); assertEquals("SUCCESS", result.await().stringValue("status"))
    }
    @Test fun invalidGoalsAndIdsDoNotReachRuntime() = runTest {
        var starts = 0; val handler = RemoteGoalCommands({ starts++ }, {}, { state() }, { 1000 })
        for (raw in listOf(command(goal = " "), command(goal = "x".repeat(2001)), command(id = "bad"), "[]", "{}"))
            assertEquals("FAILED", handler.submit(raw).stringValue("status"))
        assertEquals(0, starts)
    }
    @Test fun concurrentDuplicateDeliveriesStartOnlyOnce() = runTest {
        var starts = 0; val handler = RemoteGoalCommands({ starts++ }, {}, { state() }, { 1000 }); val raw = command()
        (1..20).map { async { handler.submit(raw) } }.awaitAll(); assertEquals(1, starts)
    }
    @Test fun staleStopCannotCancelSubsequentLocalTask() = runTest {
        val loop = GoalLoop({ _, _ -> awaitCancellation() }, { state() }, {}, {})
        loop.start(this, "旧任务"); val old = loop.state.value.taskId
        loop.stopAndAwait(old)
        loop.start(this, "新任务"); val current = loop.state.value.taskId
        assertNotEquals(old, current)
        try { loop.stopAndAwait(old); fail("stale stop accepted") } catch (_: IllegalStateException) { }
        assertTrue(loop.active); loop.stopAndAwait(current); assertFalse(loop.active)
    }
}
