// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.util.UUID

interface EnhancedControl {
    fun available(): Boolean
    suspend fun execute(action: String, params: JsonObject): JsonObject
}

/** Paired companion pulls one command, obtains a short execution lease, then acknowledges it.
 * Cancellation denies unstarted work and drains leased work before returning. Never replays. */
class CompanionControlHub(private val clock: () -> Long = System::currentTimeMillis) : EnhancedControl {
    private data class Work(val id: String, val session: String, val action: String, val params: JsonObject,
        val result: CompletableDeferred<JsonObject> = CompletableDeferred(), var leased: Boolean = false, var cancelled: Boolean = false)
    private val lock = Any()
    private val queue = Channel<Work>(1)
    private val pending = mutableMapOf<String, Work>()
    private var session = ""
    private var seen = 0L
    private var enabled = false
    val screen = MutableStateFlow<ControlledScreen?>(null)
    private var screenSeen = 0L
    fun currentScreen(): ControlledScreen? = synchronized(lock) {
        screen.value?.takeIf { clock() - screenSeen in 0..35000 }
    }
    fun updateScreen(display: ControlledScreen) = synchronized(lock) { screen.value = display; screenSeen = clock() }

    override fun available() = synchronized(lock) { enabled && clock() - seen in 0..35000 }

    override suspend fun execute(action: String, params: JsonObject): JsonObject {
        require(action in setOf("TAP", "SWIPE", "INPUT"))
        val work = synchronized(lock) {
            check(available()) { "被控端增强控制未连接，请开启共享与本应用无障碍服务" }
            check(pending.isEmpty()) { "增强控制正在执行其他动作" }
            val current = currentScreen()
            val expected = params["screen"]?.jsonObject?.let(ControlledScreen::parse)
            check(expected == null || expected.sameCoordinates(current)) { "被控端屏幕方向或尺寸已改变，请重新截图" }
            val withScreen = buildJsonObject {
                params.forEach { (k,v) -> put(k,v) }
                (expected ?: current)?.let { put("screen",it.json()) }
            }
            Work(UUID.randomUUID().toString(), session, action, withScreen).also { pending[it.id] = it }
        }
        try {
            check(queue.trySend(work).isSuccess) { "增强控制队列繁忙" }
            val result = withTimeout(12000) { work.result.await() }
            check(result.stringValue("status") == "SUCCESS") {
                if(result.stringValue("code", "") == "SCREEN_CHANGED") "被控端屏幕方向或尺寸已改变，请重新截图"
                else "被控端增强控制失败：${result.stringValue("code", "UNKNOWN")}"
            }
            return buildJsonObject {
                put("delivery", "companion_accessibility"); put("textVerified", result.boolValue("textVerified", false))
                put("needsHidConfirm", result.boolValue("needsHidConfirm", false)); put("screenResultVerified", false)
            }
        } catch (cancelled: CancellationException) {
            val leased = synchronized(lock) { work.cancelled = true; work.leased }
            if (leased) withContext(NonCancellable) {
                // Companion starts within 1s of its lease request, gestures last at most 2s.
                withTimeoutOrNull(3500) { work.result.await() }
            }
            throw cancelled
        } finally {
            synchronized(lock) { pending.remove(work.id) }
            // A cancelled request may still occupy the channel before the companion polls.
            while (true) {
                val stale = queue.tryReceive().getOrNull() ?: break
                if (stale !== work) { queue.trySend(stale); break }
            }
        }
    }

    suspend fun next(client: String, ready: Boolean, display: JsonObject? = null): JsonObject? {
        require(client.matches(Regex("[A-Za-z0-9-]{16,64}")))
        val reported = display?.let(ControlledScreen::parse)
        synchronized(lock) {
            if (session != client) { screen.value = null; screenSeen = 0 }
            if (session != client || !ready) {
                pending.values.forEach { if (!it.leased) { it.cancelled = true; it.result.complete(failed("SESSION_CHANGED")) } }
            }
            session = client; seen = clock(); enabled = ready
            reported?.let { updateScreen(it) }
        }
        if (!ready) { delay(1000); return null }
        return withTimeoutOrNull(15000) {
            while (true) {
                val work = queue.receive()
                val valid = synchronized(lock) { pending[work.id] === work && !work.cancelled && work.session == client && !work.result.isCompleted }
                if (valid) return@withTimeoutOrNull buildJsonObject {
                    put("id", work.id); put("action", work.action); put("params", work.params)
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }
    fun lease(id: String, client: String): Boolean = synchronized(lock) {
        val work = pending[id] ?: return false
        if (work.cancelled || work.leased || work.result.isCompleted || client != work.session || !available()) return false
        work.leased = true
        true
    }
    fun accept(input: JsonObject): Boolean = synchronized(lock) {
        val work = pending[input.stringValue("id")] ?: return false
        if (!work.leased || input.stringValue("session") != work.session || work.result.isCompleted) return false
        require(input.stringValue("status") in setOf("SUCCESS", "FAILED"))
        val code = input.stringValue("code", "")
        require(code.matches(Regex("[A-Z_]{0,64}")))
        work.result.complete(buildJsonObject {
            put("status", input.stringValue("status")); put("code", code)
            put("textVerified", input.boolValue("textVerified", false)); put("needsHidConfirm", input.boolValue("needsHidConfirm", false))
        })
    }
    fun stop() = synchronized(lock) {
        enabled = false; seen = 0; screen.value = null; screenSeen = 0
        pending.values.forEach { it.cancelled = true; if (!it.leased) it.result.complete(failed("STOPPED")) }
        while (queue.tryReceive().isSuccess) { }
    }
    fun status() = synchronized(lock) { buildJsonObject { put("online", clock() - seen in 0..35000); put("enabled", available()); currentScreen()?.let { put("screen",it.json()) } } }
    private fun failed(code: String) = buildJsonObject { put("status", "FAILED"); put("code", code) }
}
