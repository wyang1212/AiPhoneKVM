// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.UUID
import kotlin.math.*

interface HidOutput {
    fun connected(): Boolean
    fun report(id: Int, bytes: ByteArray): Boolean
}

class CommandEngine(
    private val hid: HidOutput,
    private val config: () -> AutomationConfig,
    private val save: (AutomationConfig) -> Unit,
    private val status: () -> JsonObject,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val enhanced: EnhancedControl? = null,
    private val cameraPointer: CameraPointer? = null
) {
    private val mutex = Mutex()
    private data class Cached(val payload: JsonObject, val response: JsonObject, val time: Long)
    private val cache = linkedMapOf<String, Cached>()
    @Volatile var busy = false; private set

    suspend fun execute(raw: String): JsonObject = mutex.withLock {
        var id = ""
        var command: JsonObject? = null
        try {
            require(raw.toByteArray(Charsets.UTF_8).size <= 16384) { "命令超过16KiB" }
            command = Json.parseToJsonElement(raw).jsonObject
            id = command.stringValue("id", command.stringValue("message_id", UUID.randomUUID().toString()))
            require(id.length in 1..128) { "命令ID长度无效" }
            require(command.stringValue("version", "1.0") == "1.0") { "仅支持协议1.0" }
            val action = command.stringValue("action").uppercase(Locale.ROOT)
            cache.entries.removeAll { clock() - it.value.time > 300000 }
            cache[id]?.let {
                return@withLock if (it.payload == command) it.response else response(id, "FAILED", "ID_CONFLICT", "同一ID不能用于不同命令")
            }
            val p = command["params"]?.jsonObject ?: JsonObject(emptyMap())
            busy = true
            val result = dispatch(action, p)
            val out = response(id, "SUCCESS", data = result)
            remember(id, command, out)
            out
        } catch (e: CancellationException) {
            command?.let { remember(id, it, response(id, "FAILED", "CANCELLED", "命令已停止；可能已经部分执行")) }
            throw e
        } catch (e: Exception) {
            val out = response(id, "FAILED", if (e is HidFailure) "HID_UNAVAILABLE" else "INVALID_PARAMS", e.message ?: "命令无效")
            command?.let { remember(id, it, out) }
            out
        } finally {
            busy = false
            withContext(NonCancellable) { releaseAll() }
        }
    }

    private fun remember(id: String, payload: JsonObject, response: JsonObject) {
        cache[id]=Cached(payload,response,clock())
        while(cache.size>128) cache.remove(cache.keys.first())
    }

    suspend fun resetSession() = mutex.withLock {
        cache.clear()
        releaseAll()
    }

    fun releaseAll() {
        runCatching { hid.report(com.atharok.btremote.common.utils.GamepadReport.ID, com.atharok.btremote.common.utils.GamepadReport.neutral()) }
        runCatching { hid.report(1, byteArrayOf(0, 0)) }
        runCatching { hid.report(2, byteArrayOf(0, 0, 0, 0)) }
        runCatching { hid.report(3, byteArrayOf(0, 0)) }
    }

    private fun send(id: Int, vararg bytes: Int) {
        if (!hid.connected() || !hid.report(id, bytes.map { it.toByte() }.toByteArray())) throw HidFailure()
    }
    private fun duration(p: JsonObject, default: Int, max: Int = 10000): Int =
        p.intValue("duration", default).also { require(it in 1..max) { "duration必须为1–$max ms" } }

    private suspend fun key(name: String, modifiers: Int = 0) {
        val media = mapOf("HOME" to 0x223, "BROWSER_HOME" to 0x223, "BACK" to 0x224, "BROWSER_BACK" to 0x224,
            "VOLUME_UP" to 0xE9, "VOLUME_DOWN" to 0xEA, "MUTE" to 0xE2, "PLAY_PAUSE" to 0xCD,
            "NEXT_TRACK" to 0xB5, "PREV_TRACK" to 0xB6, "DPAD_CENTER" to 0x214)
        val n = name.uppercase(Locale.ROOT)
        if (n in media) {
            val usage = media.getValue(n); send(3, usage and 255, usage shr 8); pause(40); send(3, 0, 0)
        } else {
            stroke(modifiers,keyCode(n))
            return
        }
        pause(40)
    }

    private suspend fun stroke(modifiers: Int, code: Int) {
        // Release the ordinary key before releasing modifiers, matching a physical keyboard.
        // Some IMEs interpret simultaneous Shift/key release as a language-toggle shortcut.
        if(modifiers!=0) { send(1,modifiers,0);pause(20) }
        send(1,modifiers,code);pause(40)
        send(1,modifiers,0)
        if(modifiers!=0) { pause(20);send(1,0,0) }
        pause(40)
    }

    private fun keyCode(n: String): Int {
        val special = mapOf("ENTER" to 40, "ESCAPE" to 41, "ESC" to 41, "BACKSPACE" to 42, "TAB" to 43,
            "SPACE" to 44, "DELETE" to 76, "RIGHT" to 79, "LEFT" to 80, "DOWN" to 81, "UP" to 82,
            "PAGE_UP" to 75, "PAGE_DOWN" to 78, "END" to 77, "INSERT" to 73, "CAPS_LOCK" to 57)
        return when {
            n in special -> special.getValue(n)
            n.length == 1 && n[0] in 'A'..'Z' -> n[0] - 'A' + 4
            n.length == 1 && n[0] in '1'..'9' -> n[0] - '1' + 30
            n == "0" -> 39
            n.matches(Regex("F([1-9]|1[0-2])")) -> 57 + n.drop(1).toInt()
            else -> throw IllegalArgumentException("不支持的按键：$n")
        }
    }

    private fun ascii(c: Char): Pair<Int, Int> {
        if (c in 'a'..'z') return 0 to (c - 'a' + 4)
        if (c in 'A'..'Z') return 2 to (c - 'A' + 4)
        if (c in '1'..'9') return 0 to (c - '1' + 30)
        if (c == '0') return 0 to 39
        val plain = "-=[]\\;'`,./"
        val shifted = "_+{}|:\"~<>?"
        val codes = listOf(45, 46, 47, 48, 49, 51, 52, 53, 54, 55, 56)
        plain.indexOf(c).takeIf { it >= 0 }?.let { return 0 to codes[it] }
        shifted.indexOf(c).takeIf { it >= 0 }?.let { return 2 to codes[it] }
        val digits = "!@#$%^&*()"
        digits.indexOf(c).takeIf { it >= 0 }?.let { return 2 to (30 + it) }
        return when (c) { ' ' -> 0 to 44; '\n' -> 0 to 40; '\t' -> 0 to 43
            else -> throw IllegalArgumentException("INPUT仅支持英文ASCII；中文请由目标输入法转换") }
    }

    private suspend fun move(dx: Int, dy: Int, ms: Int, buttons: Int = 0, c: AutomationConfig = config(), stepByStep: Boolean = false) {
        val steps = max(1, max(ceil(max(abs(dx), abs(dy)).toDouble() / if (stepByStep) 1 else 127).toInt(), ceil(ms.toDouble() / c.moveDelayMs).toInt()))
        var lastX = 0; var lastY = 0
        repeat(steps) { i ->
            currentCoroutineContext().ensureActive()
            val x = (dx.toDouble() * (i + 1) / steps).roundToInt()
            val y = (dy.toDouble() * (i + 1) / steps).roundToInt()
            send(2, buttons, (x - lastX) * if(c.invertX) -1 else 1, (y - lastY) * if(c.invertY) -1 else 1, 0)
            lastX = x; lastY = y
            pause(max(c.moveDelayMs.toLong(), ms.toLong() / steps))
        }
    }

    private suspend fun position(x: Int, y: Int, c: AutomationConfig) {
        if(cameraPointer?.enabled()==true) { cameraPointer.position(x,y);return }
        // Match BlueHIDFlow: clamp pointer at the upper-left, then use calibrated relative motion.
        val resetSteps = ceil(max(c.screenWidth, c.screenHeight) / c.sensitivity / 127).toInt() + 8
        repeat(resetSteps) { send(2, 0, -127 * if(c.invertX) -1 else 1, -127 * if(c.invertY) -1 else 1, 0); pause(c.moveDelayMs.toLong()) }
        val dx = (x / c.sensitivity).roundToInt(); val dy = (y / c.sensitivity).roundToInt()
        move(dx, dy, max(200, max(abs(dx), abs(dy)) * 2), c = c)
    }
    private fun checkPoint(x: Int, y: Int, c: AutomationConfig) {
        require(x in 0 until c.screenWidth && y in 0 until c.screenHeight) { "坐标超出目标屏幕范围" }
    }

    private suspend fun dispatch(action: String, p: JsonObject): JsonObject {
        val c = config()
        when (action) {
            "STATUS", "GET_STATUS" -> return status()
            "GET_CONFIG" -> {
                val all = c.json(); val section = p.stringValue("section", "")
                return if (section.isBlank()) all else buildJsonObject { put(section, all[section] ?: throw IllegalArgumentException("配置节不存在")) }
            }
            "SET_CONFIG" -> { val next = c.merge(p); save(next); return next.json() }
            "SWITCH_MODE" -> {
                val next = c.copy(mode = p.stringValue("mode").lowercase()).validate(); save(next)
                return buildJsonObject { put("mode", next.mode); put("restartRequired", false); put("combinedHid", true) }
            }
            "WAIT" -> { pause(duration(p, 1000, 30000).toLong()); return buildJsonObject {} }
            "CALIBRATE" -> {
                val phone = buildJsonObject {
                    p["sensitivity"]?.let { put("hidSensitivity", it) }; p["move_delay"]?.let { put("moveDelayMs", it) }
                }
                val next = c.merge(buildJsonObject { put("phone", phone) })
                val dx = p.intValue("hid_x", 0); val dy = p.intValue("hid_y", 0)
                require(abs(dx.toLong()) <= 5000 && abs(dy.toLong()) <= 5000) { "校准位移不能超过5000" }
                val delayMs = p.intValue("delay_ms", next.moveDelayMs).also { require(it in 8..100) }
                val step = p.boolValue("step_by_step", false)
                require(!step || max(abs(dx), abs(dy)) <= 300) { "逐步校准最多300步" }
                save(next)
                if(dx != 0 || dy != 0) move(dx, dy, delayMs * if(step) max(abs(dx),abs(dy)) else 1, c = next.copy(moveDelayMs = delayMs), stepByStep = step)
                return next.json()
            }
            "TAP" -> {
                val x = p.intValue("x"); val y = p.intValue("y"); checkPoint(x,y,c); val ms = duration(p,50)
                if (cameraPointer?.enabled()!=true && enhanced?.available() == true) {
                    require(ms <= 2000) { "增强手势最长2000ms" }
                    return enhanced.execute("TAP", buildJsonObject { put("x", x); put("y", y); put("duration", ms);p["screen"]?.let { put("screen",it) } })
                }
                position(x,y,c); send(2,1,0,0,0); pause(ms.toLong()); send(2,0,0,0,0)
            }
            "SWIPE", "SWIPE_UP", "SWIPE_DOWN", "SWIPE_LEFT", "SWIPE_RIGHT" -> {
                val cx = c.screenWidth/2; val cy = c.screenHeight/2
                val points = when(action) {
                    "SWIPE_UP" -> listOf(cx, c.screenHeight*4/5, cx,c.screenHeight/5)
                    "SWIPE_DOWN" -> listOf(cx,c.screenHeight/5,cx,c.screenHeight*4/5)
                    "SWIPE_LEFT" -> listOf(c.screenWidth*9/10,cy,c.screenWidth/10,cy)
                    "SWIPE_RIGHT" -> listOf(c.screenWidth/10,cy,c.screenWidth*9/10,cy)
                    else -> listOf(p.intValue("from_x"),p.intValue("from_y"),p.intValue("to_x"),p.intValue("to_y"))
                }
                val (x,y,tx,ty) = points; checkPoint(x,y,c); checkPoint(tx,ty,c); val ms = duration(p,300)
                if (cameraPointer?.enabled()!=true && enhanced?.available() == true) {
                    require(ms <= 2000) { "增强手势最长2000ms" }
                    return enhanced.execute("SWIPE", buildJsonObject { put("from_x",x);put("from_y",y);put("to_x",tx);put("to_y",ty);put("duration",ms);p["screen"]?.let { put("screen",it) } })
                }
                if(cameraPointer?.enabled()==true) {
                    require(abs(ty-y)>=abs(tx-x)) { "摄像头蓝牙模式仅支持竖向滚轮，横向触屏滑动不可用" }
                    position(x,y,c)
                    val wheel=((ty-y).toDouble()/c.screenHeight*8).roundToInt().coerceIn(-6,6)
                    require(wheel!=0) { "滚动距离过小" }
                    send(2,0,0,0,wheel);pause(ms.toLong());send(2,0,0,0,0)
                    return buildJsonObject { put("delivery","bluetooth_mouse_wheel_sent");put("screenResultVerified",false) }
                }
                position(x,y,c); send(2,1,0,0,0); pause(50)
                move(((tx-x)/c.sensitivity).roundToInt(),((ty-y)/c.sensitivity).roundToInt(),ms,1,c)
                send(2,0,0,0,0)
            }
            "INPUT" -> {
                val text = p.stringValue("text"); require(text.length in 1..256) { "文本长度必须为1–256" }
                val confirm = p.stringValue("confirm", "").lowercase()
                require(confirm in listOf("", "enter", "space")) { "confirm仅支持enter或space" }
                val replace = p.boolValue("replace", false)
                if (cameraPointer?.enabled()!=true && enhanced?.available() == true) {
                    val response = enhanced.execute("INPUT",buildJsonObject { put("text",text);put("confirm",confirm);put("replace",replace);p["screen"]?.let { put("screen",it) } })
                    check(response.boolValue("textVerified",false)) { "被控端未确认输入文本，请检查当前输入框" }
                    if (response.boolValue("needsHidConfirm",false)) {
                        currentCoroutineContext().ensureActive()
                        if (confirm.isNotEmpty()) key(confirm)
                    }
                    return response
                }
                val keys = text.map { ascii(it) } // Validate the entire text before emitting anything.
                require(!replace || cameraPointer?.enabled()==true) { "替换全文需要开启被控端增强控制" }
                if(replace)key("A",1)
                for ((mod, code) in keys) stroke(mod,code)
                if(confirm.isNotEmpty()) key(confirm)
            }
            "KEY_PRESS" -> key(p.stringValue("key"))
            "KEY_COMBO" -> {
                val mods = p["modifiers"]?.jsonArray ?: throw IllegalArgumentException("缺少modifiers")
                val bits = mapOf("ctrl" to 1,"shift" to 2,"alt" to 4,"gui" to 8,"meta" to 8)
                val mask = mods.fold(0) { mask, m -> mask or (bits[m.jsonPrimitive.content.lowercase()] ?: throw IllegalArgumentException("不支持的修饰键")) }
                key(p.stringValue("key"),mask)
            }
            "HOME", "BACK", "VOLUME_UP", "VOLUME_DOWN", "MUTE", "PLAY_PAUSE", "NEXT_TRACK", "PREV_TRACK" -> key(action)
            "RELEASE_ALL" -> releaseAll()
            "CAPTURE", "CAMERA_START", "CAMERA_STOP", "CAMERA_STATUS" -> throw IllegalArgumentException("摄像头需在主控设备页显式开启，不允许通过远程命令打开相机")
            else -> throw IllegalArgumentException("不支持的命令：$action")
        }
        return buildJsonObject { put("delivery", "hid_report_sent"); put("screenResultVerified", false) }
    }
    private class HidFailure : Exception("蓝牙未连接或HID报告发送失败")

    companion object {
        fun response(id: String, state: String, code: String = "", error: String = "", data: JsonObject? = null): JsonObject = buildJsonObject {
            put("version", "1.0"); put("id", id); put("message_id", id); put("status", state); put("timestamp", System.currentTimeMillis())
            if(code.isNotEmpty()) put("code",code)
            if(error.isNotEmpty()) put("error",error)
            data?.let { put("data",it) }
        }
    }
}
