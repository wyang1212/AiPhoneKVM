// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.UUID

enum class GoalOutcome { CONTINUE, COMPLETED, BLOCKED }

data class GoalDecision(val outcome: GoalOutcome, val summary: String, val evidence: String, val command: JsonObject?) {
    companion object {
        fun parse(raw: String): GoalDecision {
            val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = Json.parseToJsonElement(text).jsonObject
            val plan = VisionPlan.parse(text)
            val outcome = when (obj.stringValue("taskStatus")) {
                "continue" -> GoalOutcome.CONTINUE
                "completed" -> GoalOutcome.COMPLETED
                "blocked" -> GoalOutcome.BLOCKED
                else -> error("模型未返回有效任务状态")
            }
            val evidence = obj.stringValue("evidence").also { require(it.isNotBlank() && it.length <= 2000) { "模型缺少屏幕判断依据" } }
            require((outcome == GoalOutcome.CONTINUE) == (plan.command != null)) { "继续任务必须有一步动作；完成或受阻时不能包含动作" }
            return GoalDecision(outcome, plan.summary, evidence, plan.command)
        }
    }
}

data class GoalObservation(val decision: GoalDecision, val fingerprint: String)
data class GoalLimits(val maxSteps: Int = 30, val maxMinutes: Int = 15) {
    fun validate(): GoalLimits {
        require(maxSteps in 1..120 && maxMinutes in 1..60) { "操作步数应为1–120，运行时长应为1–60分钟" }
        return this
    }
}

enum class GoalTaskStatus(val label: String) {
    IDLE("尚未开始"), RUNNING("正在执行"), STOPPING("正在停止"), COMPLETED("已完成"),
    BLOCKED("需要处理"), FAILED("执行失败"), CANCELLED("已停止"), LIMIT_REACHED("达到运行上限")
}

data class GoalTaskState(
    val status: GoalTaskStatus = GoalTaskStatus.IDLE,
    val goal: String = "",
    val steps: Int = 0,
    val rounds: Int = 0,
    val limits: GoalLimits = GoalLimits(),
    val summary: String = "",
    val message: String = "",
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val taskId: String = ""
) {
    val active get() = status == GoalTaskStatus.RUNNING || status == GoalTaskStatus.STOPPING
    fun json() = buildJsonObject {
        put("status", status.name.lowercase(Locale.ROOT)); put("label", status.label); put("active", active)
        put("goal", goal); put("steps", steps); put("rounds", rounds)
        put("maxSteps", limits.maxSteps); put("maxMinutes", limits.maxMinutes)
        put("summary", summary); put("message", message); put("startedAt", startedAt); put("endedAt", endedAt)
        put("taskId", taskId)
    }
}

/** One fresh observation per action. A sent HID report is never a completion verdict. */
class GoalLoop(
    private val observe: suspend (String, String) -> GoalObservation,
    private val execute: suspend () -> JsonObject,
    private val interrupt: () -> Unit,
    private val drain: suspend () -> Unit,
    private val event: (String) -> Unit = {},
    private val settle: suspend () -> Unit = { delay(1000) },
    private val clock: () -> Long = System::currentTimeMillis
) {
    val state = MutableStateFlow(GoalTaskState())
    val active get() = state.value.active
    private val lock = Any()
    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, goal: String, limits: GoalLimits = GoalLimits()) = synchronized(lock) {
        require(goal.isNotBlank() && goal.length <= 2000) { "请填写操作目标（最多2000字）" }
        limits.validate()
        check(!active && job?.isCompleted != false) { "目标任务尚未结束" }
        check(scope.isActive) { "自动化会话已停止" }
        state.value = GoalTaskState(GoalTaskStatus.RUNNING, goal.trim(), limits = limits, startedAt = clock(), message = "正在获取新截图", taskId = UUID.randomUUID().toString())
        job = scope.launch(start = CoroutineStart.ATOMIC) { runTask() }
    }

    fun cancel() {
        synchronized(lock) {
            if (!active) return
            state.value = state.value.copy(status = GoalTaskStatus.STOPPING, message = "正在取消识别与操作并松键")
            job?.cancel()
        }
        interrupt()
    }

    suspend fun stopAndAwait(expectedTaskId: String? = null) {
        val old = synchronized(lock) {
            check(expectedTaskId == null || state.value.taskId == expectedTaskId) { "任务已变化，请刷新状态" }
            cancel()
            job
        }
        if (old != currentCoroutineContext()[Job]) old?.join()
    }

    fun reset() = synchronized(lock) {
        check(!active && job?.isCompleted != false) { "请先等待目标任务停止" }
        state.value = GoalTaskState()
    }

    private suspend fun runTask() {
        var terminal = GoalTaskStatus.FAILED
        var message = "任务未完成"
        try {
            event("AI目标任务已开始")
            val finished = withTimeoutOrNull(state.value.limits.maxMinutes * 60_000L) {
                val history = ArrayDeque<String>()
                var lastFingerprint = ""
                var lastAction = ""
                var repeated = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    state.value = state.value.copy(rounds = state.value.rounds + 1, message = "截图并识别当前进度")
                    val observation = observe(state.value.goal, history.joinToString("\n"))
                    currentCoroutineContext().ensureActive()
                    val decision = observation.decision
                    state.value = state.value.copy(summary = decision.summary, message = decision.evidence)
                    when (decision.outcome) {
                        GoalOutcome.COMPLETED -> { terminal = GoalTaskStatus.COMPLETED; message = decision.evidence; break }
                        GoalOutcome.BLOCKED -> { terminal = GoalTaskStatus.BLOCKED; message = decision.evidence; break }
                        GoalOutcome.CONTINUE -> Unit
                    }
                    if (state.value.steps >= state.value.limits.maxSteps) {
                        terminal = GoalTaskStatus.LIMIT_REACHED; message = "已达到操作步数上限，尚未确认目标完成"; break
                    }
                    val action = decision.command.toString()
                    repeated = if (action==lastAction && CameraSceneFingerprint.similarOrSame(observation.fingerprint,lastFingerprint)) repeated+1 else 1
                    lastFingerprint=observation.fingerprint;lastAction=action
                    if (repeated >= 3) {
                        terminal = GoalTaskStatus.BLOCKED; message = "相同画面和动作连续出现，已停止重复操作"; break
                    }
                    currentCoroutineContext().ensureActive()
                    val response = execute()
                    currentCoroutineContext().ensureActive()
                    check(response.stringValue("status") == "SUCCESS") { "控制动作未成功：${response.stringValue("error", "请检查连接与参数").take(160)}" }
                    val steps = state.value.steps + 1
                    state.value = state.value.copy(steps = steps, message = "已执行第${steps}步，等待画面更新")
                    history.addLast("第${steps}步：${decision.command?.stringValue("action")} 已发送；操作前观察：${decision.summary.take(300)}")
                    while (history.size > 6) history.removeFirst()
                    settle()
                }
                true
            }
            if (finished == null) { terminal = GoalTaskStatus.LIMIT_REACHED; message = "已达到运行时长上限，尚未确认目标完成" }
        } catch (_: TimeoutCancellationException) {
            terminal = GoalTaskStatus.FAILED; message = "截图或识别等待超时，请检查截图助手与网络"
        } catch (_: CancellationException) {
            terminal = GoalTaskStatus.CANCELLED; message = "任务已停止，已执行的操作不会撤销"
        } catch (error: Exception) {
            terminal = if (currentCoroutineContext().isActive) GoalTaskStatus.FAILED else GoalTaskStatus.CANCELLED
            message = if (terminal == GoalTaskStatus.CANCELLED) "任务已停止" else error.message?.take(200) ?: "任务执行失败"
        } finally {
            withContext(NonCancellable) {
                try { interrupt(); drain() } catch (_: Exception) {
                    terminal = GoalTaskStatus.FAILED; message = "停止操作时发生错误，请停止自动化后检查连接"
                }
                synchronized(lock) {
                    state.value = state.value.copy(status = terminal, message = message, endedAt = clock())
                    job = null
                }
                event("AI目标任务 · ${terminal.label}")
            }
        }
    }
}
