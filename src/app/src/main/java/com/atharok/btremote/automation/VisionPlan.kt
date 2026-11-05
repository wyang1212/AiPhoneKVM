// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.serialization.json.*

/** Model output is data: it cannot change configuration or invoke arbitrary endpoints. */
data class VisionPlan(val summary: String, val command: JsonObject?) {
    companion object {
        fun parse(raw: String): VisionPlan {
            val text=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root=Json.parseToJsonElement(text)
            require(root is JsonObject) { "模型响应必须是JSON对象" }
            val obj=root
            val summary=obj.stringValue("summary").also { require(it.length in 1..4000) }
            val commands=obj["commands"] ?: JsonArray(emptyList())
            require(commands is JsonArray) { "模型commands必须是数组" }
            require(commands.size<=1) { "模型一次只能建议一步操作" }
            val command=commands.firstOrNull()?.let { input ->
                require(input is JsonObject) { "模型动作必须是包含action和params的对象，不能是字符串" }
                val action=input.stringValue("action").uppercase()
                require(action in setOf("TAP","SWIPE","SWIPE_UP","SWIPE_DOWN","SWIPE_LEFT","SWIPE_RIGHT","INPUT","KEY_PRESS","KEY_COMBO","HOME","BACK","VOLUME_UP","VOLUME_DOWN","WAIT")) { "模型建议的动作不受支持" }
                val params=input["params"] ?: JsonObject(emptyMap())
                require(params is JsonObject) { "模型动作params必须是JSON对象" }
                buildJsonObject { put("action",action); put("params",params) }
            }
            return VisionPlan(summary,command)
        }
    }
}
