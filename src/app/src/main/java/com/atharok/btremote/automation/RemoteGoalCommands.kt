// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.UUID

/** Immediate, bounded MQTT requests. No offline task queue or automatic retry. */
class RemoteGoalCommands(
    private val start: (String) -> Unit,
    private val stop: suspend (String) -> Unit,
    private val state: () -> JsonObject,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private data class Receipt(val signature: String, val response: JsonObject)
    private val receipts = linkedMapOf<String, Receipt>()

    suspend fun submit(raw: String): JsonObject = mutex.withLock {
        val id = runCatching { Json.parseToJsonElement(raw).jsonObject.stringValue("id") }.getOrDefault("")
        var signature: String? = null
        val result = try {
            val command = Json.parseToJsonElement(raw).jsonObject
            require(UUID.fromString(id).toString() == id) { "请求编号无效" }
            require(command.stringValue("version", "1.0") == "1.0") { "仅支持协议1.0" }
            val action = command.stringValue("action")
            require(action in setOf("AI_START", "AI_STOP")) { "不支持这个操作" }
            val params = command["params"]?.jsonObject ?: error("缺少任务参数")
            val value = params.stringValue(if (action == "AI_START") "goal" else "taskId").trim()
            signature = "$action:$value"
            receipts[id]?.let {
                require(it.signature == signature) { "同一请求编号不能用于不同任务" }
                return@withLock it.response
            }
            if (action == "AI_START") {
                require(value.isNotBlank() && value.length <= 2000) { "请填写任务目标（最多2000字）" }
                val expires = params["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0
                require(expires > clock() && expires - clock() <= 30000) { "启动请求已过期或手机时间不正确，请刷新状态后重试" }
                start(value)
            } else {
                require(UUID.fromString(value).toString() == value) { "任务编号无效，请刷新状态" }
                stop(value)
            }
            CommandEngine.response(id, "SUCCESS", data = state())
        } catch (error: IllegalArgumentException) {
            CommandEngine.response(id, "FAILED", "INVALID_TASK", error.message ?: "任务参数无效")
        } catch (error: IllegalStateException) {
            CommandEngine.response(id, "FAILED", "TASK_UNAVAILABLE", error.message ?: "当前无法启动任务")
        }
        // A rejected busy request must not turn into a new task when MQTT redelivers it later.
        if (signature != null && !receipts.containsKey(id)) {
            receipts[id] = Receipt(signature, result)
            while (receipts.size > 128) receipts.remove(receipts.keys.first())
        }
        result
    }
}
