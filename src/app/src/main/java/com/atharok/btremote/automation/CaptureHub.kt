// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.util.UUID

/** Fresh observation from B's companion or A's foreground camera; never A's screenshot. */
class CaptureHub(private val onFrame: (Frame) -> Unit = {}) {
    data class Frame(val bytes: ByteArray, val capturedAt: Long, val receivedAt: Long, val width: Int, val height: Int, val screen: ControlledScreen? = null, val source: String = "companion", val sourceRevision: Long = 0)
    @Volatile var localProvider: (suspend () -> Frame)? = null
    @Volatile private var localJob: Job? = null
    private data class Request(val id: String, val result: CompletableDeferred<Frame>)
    private val requests = Channel<Request>(1)
    private val pending = mutableMapOf<String, Request>()
    val lastFrame = MutableStateFlow<Frame?>(null)
    val companionSeen = MutableStateFlow(0L)
    @Volatile private var captureActive=false
    val capturing get() = captureActive

    suspend fun capture(): Frame {
        synchronized(pending) { check(!captureActive) { "已有画面请求正在进行" };captureActive=true }
        try { return captureOne() } finally { captureActive=false }
    }
    private suspend fun captureOne(): Frame {
        localProvider?.let { provider ->
            check(localJob?.isActive != true && synchronized(pending) { pending.isEmpty() }) { "已有画面请求正在进行" }
            localJob=currentCoroutineContext()[Job]
            try {
                val frame=provider()
                val context=currentCoroutineContext()
                val job=context[Job]
                synchronized(pending) {
                    context.ensureActive()
                    check(localJob===job && localProvider===provider) { "画面来源已失效" }
                    lastFrame.value=frame
                }
                return frame
            } finally { localJob=null }
        }
        val r = Request(UUID.randomUUID().toString(), CompletableDeferred())
        synchronized(pending) {
            check(pending.isEmpty()) { "已有截图请求正在进行" }
            pending[r.id] = r
        }
        try {
            check(requests.trySend(r).isSuccess) { "截图队列繁忙" }
            return withTimeout(25000) { r.result.await() }
        } finally { synchronized(pending) { pending.remove(r.id) } }
    }
    suspend fun next(): JsonObject? {
        companionSeen.value = System.currentTimeMillis()
        return withTimeoutOrNull(15000) {
            while(true) {
                val r = requests.receive()
                if(synchronized(pending) { pending.containsKey(r.id) }) return@withTimeoutOrNull buildJsonObject { put("id", r.id) }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }
    fun accept(body: String): Boolean {
        val p = Json.parseToJsonElement(body).jsonObject
        val id = p.stringValue("id")
        val r = synchronized(pending) { pending[id] } ?: return false
        p["error"]?.let { r.result.completeExceptionally(IllegalStateException("被控端截图失败：" + it.jsonPrimitive.content.take(160))); return true }
        try {
            val bytes = Base64.decode(p.stringValue("image"), Base64.NO_WRAP)
            require(bytes.size in 100..3000000 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) { "需要有效JPEG，最大3MB" }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth in 100..4096 && options.outHeight in 100..4096) { "图片尺寸无效" }
            val now = System.currentTimeMillis()
            val timestamp = p["captured_at"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("缺少截图时间")
            val age = p.intValue("age_ms")
            require(age in 0..5000) { "被控端画面已过期，请重新开启屏幕共享" }
            val screen = p["display"]?.jsonObject?.let(ControlledScreen::parse)
            val frame = Frame(bytes,timestamp,now,options.outWidth,options.outHeight,screen)
            return synchronized(pending) {
                if (pending[id] !== r || !r.result.isActive) false
                else {
                    onFrame(frame)
                    lastFrame.value = frame; companionSeen.value = now
                    r.result.complete(frame)
                }
            }
        } catch(e: Exception) {
            r.result.completeExceptionally(IllegalStateException(e.message ?: "被控端回传图片无效"))
            throw e
        }

    }
    fun cancelPending() {
        synchronized(pending) {
            localJob?.cancel();localJob=null
            pending.values.forEach { it.result.cancel() }; pending.clear()
        }
        while(requests.tryReceive().isSuccess) { /* clear stale requests */ }
    }
    fun stop() {
        cancelPending()
        lastFrame.value = null; companionSeen.value = 0L
    }
    fun status() = buildJsonObject {
        put("source",if(localProvider==null) "companion" else "camera")
        put("companionOnline", System.currentTimeMillis() - companionSeen.value < 35000)
        lastFrame.value?.let { put("width",it.width); put("height",it.height); put("capturedAt",it.capturedAt); put("receivedAt",it.receivedAt) }
    }
}
