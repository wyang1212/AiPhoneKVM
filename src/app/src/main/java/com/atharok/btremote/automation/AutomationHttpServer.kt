// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class AutomationHttpServer(private val runtime: AutomationRuntime, port: Int): NanoHTTPD("0.0.0.0",port) {
    // NanoHTTPD 2.3.1 otherwise gzip-encodes an empty 204 response, breaking Android polling.
    override fun useGzipWhenAccepted(response: Response): Boolean = false

    private fun auth(session: IHTTPSession, token: String) = MessageDigest.isEqual(
        (session.headers["authorization"] ?: "").toByteArray(), "Bearer $token".toByteArray())
    override fun serve(session: IHTTPSession): Response {
        try {
            if(session.method == Method.GET && session.uri == "/") return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",WebConsole.html).apply {
                addHeader("Cache-Control","no-store"); addHeader("X-Content-Type-Options","nosniff")
                addHeader("Content-Security-Policy","default-src 'self'; img-src 'self' blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
            }
            val c = runtime.store.state.value
            val captureRoute = session.uri in setOf("/api/capture/next","/api/capture/frame","/api/companion/poll","/api/companion/start","/api/companion/result")
            if(!auth(session,if(captureRoute) c.captureToken else c.token)) return json(Response.Status.UNAUTHORIZED,buildJsonObject { put("error","需要访问令牌") })
            if(!runtime.running.value) return json(Response.Status.SERVICE_UNAVAILABLE,buildJsonObject { put("error","服务已停止") })
            if (runtime.goalLoop.active && session.method == Method.POST && session.uri in setOf("/api/ai/settings", "/api/ai/analyze", "/api/ai/execute", "/api/capture", "/api/config")) {
                return json(Response.Status.CONFLICT, buildJsonObject { put("error", "目标任务执行中，请先停止任务") })
            }
            return when {
                session.method == Method.POST && session.uri == "/api/companion/poll" -> {
                    val input=Json.parseToJsonElement(body(session,1024)).jsonObject
                    val request=runBlocking { runtime.companion.next(input.stringValue("session"),input.boolValue("enabled",false),input["display"]?.jsonObject) }
                    if(request==null) newFixedLengthResponse(Response.Status.NO_CONTENT,"text/plain","") else json(Response.Status.OK,request)
                }
                session.method == Method.POST && session.uri == "/api/companion/start" -> {
                    val input=Json.parseToJsonElement(body(session,1024)).jsonObject
                    val allowed=runtime.companion.lease(input.stringValue("id"),input.stringValue("session"))
                    json(if(allowed) Response.Status.OK else Response.Status.CONFLICT,buildJsonObject { put("allowed",allowed) })
                }
                session.method == Method.POST && session.uri == "/api/companion/result" -> {
                    val accepted=runtime.companion.accept(Json.parseToJsonElement(body(session,2048)).jsonObject)
                    json(if(accepted) Response.Status.OK else Response.Status.CONFLICT,buildJsonObject { put("accepted",accepted) })
                }
                session.method == Method.GET && session.uri == "/api/ai/task" -> json(Response.Status.OK,runtime.goalLoop.state.value.json())
                session.method == Method.POST && session.uri == "/api/ai/task" -> {
                    val input=Json.parseToJsonElement(body(session,8192)).jsonObject
                    runtime.startGoal(input.stringValue("goal"),GoalLimits(input.intValue("maxSteps",30),input.intValue("maxMinutes",15)))
                    json(Response.Status.OK,runtime.goalLoop.state.value.json())
                }
                session.method == Method.POST && session.uri == "/api/ai/task/stop" -> {
                    runBlocking { runtime.goalLoop.stopAndAwait() }
                    json(Response.Status.OK,runtime.goalLoop.state.value.json())
                }
                session.method == Method.GET && session.uri == "/api/ai/settings" -> json(Response.Status.OK,buildJsonObject {
                    put("baseUrl",runtime.vision.baseUrl.value);put("model",runtime.vision.model.value);put("configured",runtime.vision.configured.value)
                })
                session.method == Method.POST && session.uri == "/api/ai/settings" -> {
                    require(session.remoteIpAddress in setOf("127.0.0.1","::1")) { "密钥配置请在主控端手机设置中完成" }
                    val input=Json.parseToJsonElement(body(session,8192)).jsonObject
                    runtime.vision.save(input.stringValue("baseUrl"),input.stringValue("model"),input["apiKey"]?.jsonPrimitive?.content)
                    json(Response.Status.OK,buildJsonObject { put("saved",true) })
                }
                session.method == Method.POST && session.uri == "/api/ai/analyze" -> {
                    val input=Json.parseToJsonElement(body(session,8192)).jsonObject
                    json(Response.Status.OK,runBlocking { runtime.vision.analyze(input.stringValue("goal")) })
                }
                session.method == Method.POST && session.uri == "/api/ai/execute" -> json(Response.Status.OK,runBlocking { runtime.vision.executeSuggestion() })
                session.method == Method.POST && session.uri == "/api/ai/cancel" -> {
                    runBlocking { runtime.goalLoop.stopAndAwait();runtime.vision.cancelAndAwait();runtime.cancelCommands() }
                    json(Response.Status.OK,buildJsonObject { put("cancelled",true) })
                }
                session.method == Method.GET && session.uri == "/api/status" -> json(Response.Status.OK,runtime.status())
                session.method == Method.GET && session.uri == "/api/config" -> json(Response.Status.OK,c.json())
                session.method == Method.GET && session.uri == "/api/logs" -> json(Response.Status.OK,buildJsonObject { put("logs",JsonArray(runtime.logs.value.map(::JsonPrimitive))) })
                session.method == Method.GET && session.uri == "/api/capture/next" -> {
                    val request = runBlocking { runtime.capture.next() }
                    if(request == null) newFixedLengthResponse(Response.Status.NO_CONTENT,"text/plain","") else json(Response.Status.OK,request)
                }
                session.method == Method.POST && session.uri == "/api/capture/frame" -> {
                    val ok = runtime.capture.accept(body(session,4100000))
                    json(if(ok) Response.Status.OK else Response.Status.CONFLICT,buildJsonObject { put("accepted",ok) })
                }
                session.method == Method.POST && session.uri == "/api/capture" -> {
                    val frame = runBlocking { runtime.capture.capture() }
                    json(Response.Status.OK,buildJsonObject { put("width",frame.width);put("height",frame.height);put("captured_at",frame.capturedAt);put("imageUrl","/api/capture/latest") })
                }
                session.method == Method.GET && session.uri == "/api/capture/latest" -> {
                    val frame = runtime.capture.lastFrame.value ?: return json(Response.Status.NOT_FOUND,buildJsonObject { put("error","尚无被控端截图") })
                    newFixedLengthResponse(Response.Status.OK,"image/jpeg",ByteArrayInputStream(frame.bytes),frame.bytes.size.toLong()).apply { addHeader("Cache-Control","no-store") }
                }
                session.method == Method.POST && session.uri == "/api/command" -> json(Response.Status.OK,runBlocking { runtime.submit(body(session,16384)) })
                session.method == Method.POST && session.uri == "/api/config" -> {
                    val input = Json.parseToJsonElement(body(session,16384)).jsonObject
                    val next = runtime.updateHttpConfiguration(input)
                    json(Response.Status.OK,next.json())
                }
                else -> json(Response.Status.NOT_FOUND,buildJsonObject { put("error","接口不存在") })
            }
        } catch(_: ActiveGoalConfigurationException) { return json(Response.Status.CONFLICT,buildJsonObject { put("error","目标任务执行中，请先停止任务") }) }
        catch(_: TimeoutCancellationException) { return json(Response.Status.REQUEST_TIMEOUT,buildJsonObject { put("error","被控端截图等待超时，请检查截图助手") }) }
        catch(e: Exception) { return json(Response.Status.BAD_REQUEST,buildJsonObject { put("error",e.message?.take(180) ?: "请求失败") }) }
    }
    private fun body(s: IHTTPSession,maxBytes: Int): String {
        require(s.headers["content-type"]?.substringBefore(';') == "application/json") { "需要application/json" }
        val length = s.headers["content-length"]?.toIntOrNull() ?: throw IllegalArgumentException("需要Content-Length")
        require(length in 1..maxBytes) { "请求大小超出范围" }
        val bytes = ByteArray(length)
        var pos = 0
        while(pos < length) { val n = s.inputStream.read(bytes,pos,length-pos); require(n > 0) { "请求不完整" };pos += n }
        return bytes.toString(Charsets.UTF_8)
    }
    private fun json(status: Response.Status, data: JsonObject) = newFixedLengthResponse(status,"application/json; charset=utf-8",data.toString()).apply {
        addHeader("Cache-Control","no-store");addHeader("X-Content-Type-Options","nosniff")
    }
}
