// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*
import org.koin.core.context.GlobalContext
import com.atharok.btremote.domain.repositories.BluetoothRepository
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal class ActiveGoalConfigurationException : IllegalStateException("目标任务执行中，请先停止任务")

class AutomationRuntime private constructor(private val context: Context) {
    val store = ConfigStore(context)
    val companion = CompanionControlHub()
    val cameraMode = MutableStateFlow(false)
    val camera: CameraObservation by lazy { CameraObservation(context, mouse={ x,y ->
        check(engineConnected()) { "请先开启自动化并连接所选蓝牙设备" }
        check(repo.sendReport(2,byteArrayOf(0,x.toByte(),y.toByte(),0),boundTarget)) { "蓝牙鼠标发送失败" }
    }, onLost={ reason ->
        actionGeneration.incrementAndGet()
        val input=synchronized(commandLock) { epoch.incrementAndGet();active }
        input?.cancel();capture.cancelPending();engine.releaseAll()
        goalLoop.cancel();vision.cancel();record(reason)
    },canConfigure={ !goalLoop.active && !vision.working.value && pendingCommands==0 && !engine.busy && !capture.capturing }) }
    fun usesEnhancedControl() = !cameraMode.value && companion.available()
    fun selectCamera(value:Boolean) = synchronized(commandLock) {
        check(!goalLoop.active && !vision.working.value && pendingCommands==0 && !capture.capturing && !camera.busy.value) { "请先停止任务并等待当前操作完成" }
        if(value==cameraMode.value)return@synchronized
        actionGeneration.incrementAndGet();vision.cancel();capture.stop();camera.stop(notify=false)
        cameraMode.value=value
        capture.localProvider=if(value) ({ camera.capture() }) else null
    }
    val capture = CaptureHub { frame -> frame.screen?.let { companion.updateScreen(it) } }
    fun controlConfig(): AutomationConfig = if(cameraMode.value) camera.coordinateConfig(store.state.value) else companion.currentScreen()?.applyTo(store.state.value) ?: store.state.value
    @Volatile private var goalUsesEnhancedControl = false
    val vision by lazy { VisionClient(context, this) }
    val goalLoop by lazy { GoalLoop(
        observe = { goal, history -> vision.analyzeGoal(goal, history) },
        execute = { vision.executeGoalSuggestion() },
        interrupt = { vision.cancel() },
        drain = { cancelCommands(); capture.cancelPending() },
        event = { record(it) }
    ) }
    private val remoteGoals by lazy { RemoteGoalCommands(
        start = { startGoal(it) }, stop = { goalLoop.stopAndAwait(it) },
        state = { goalLoop.state.value.json() }
    ) }
    val actionGeneration = AtomicLong()
    private val repo: BluetoothRepository get() = GlobalContext.get().get()
    val running = MutableStateFlow(false)
    val mqttState = MutableStateFlow("未配置")
    val logs = MutableStateFlow<List<String>>(emptyList())
    private var scope: CoroutineScope? = null
    private var stoppingJob: Job? = null
    @Volatile private var authorizedTarget: String? = null
    @Volatile private var boundTarget: String? = null
    private var http: AutomationHttpServer? = null
    private var mqtt: AutomationMqtt? = null
    private data class Work(val raw: String, val result: CompletableDeferred<JsonObject>, val epoch: Long)
    private var queue = Channel<Work>(16)
    private var active: Job? = null
    private val commandLock=Any()
    private var pendingCommands = 0
    private val epoch = AtomicLong()
    val engine: CommandEngine = CommandEngine(object : HidOutput {
        override fun connected() = boundTarget?.let { repo.getDeviceHidConnectionState().value.isConnectedTo(it) } == true
        override fun report(id: Int, bytes: ByteArray) = connected() && repo.sendReport(id, bytes, boundTarget)
    }, { controlConfig() }, { store.save(it) }, { status() }, enhanced = object : EnhancedControl {
        override fun available() = !cameraMode.value && ((goalLoop.active && goalUsesEnhancedControl) || companion.available())
        override suspend fun execute(action: String, params: JsonObject): JsonObject {
            check(engineConnected()) { "目标蓝牙连接已变化，增强控制已停止" }
            return companion.execute(action, params)
        }
    }, cameraPointer = object:CameraPointer {
        override fun enabled() = cameraMode.value
        override suspend fun position(x:Int,y:Int) = camera.position(x,y)
    })
    private fun engineConnected() = boundTarget?.let { repo.getDeviceHidConnectionState().value.isConnectedTo(it) } == true

    @Synchronized fun authorizeTarget(address: String) {
        check(repo.getDeviceHidConnectionState().value.isConnectedTo(address)) { "所选设备尚未连接" }
        store.bindCaptureTo(address)
        authorizedTarget = address
    }
    suspend fun stopAndAwait() = withContext(NonCancellable + Dispatchers.IO) {
        stop()
        val oldJob = synchronized(this@AutomationRuntime) { stoppingJob }
        oldJob?.join()
        goalLoop.stopAndAwait()
        goalLoop.reset()
        vision.cancelAndAwait()
        engine.resetSession()
        capture.stop()
    }

    @Synchronized fun start() {
        if(running.value) return
        check(!context.getSharedPreferences("website_binding_private", Context.MODE_PRIVATE).contains("pending")) { "网站配对尚未确认，请先在首页恢复配对" }
        val config = store.state.value.validate()
        check(stoppingJob?.isCompleted != false) { "正在停止上一会话，请稍后重试" }
        val target = authorizedTarget ?: error("请先从首页选择AI自动化和目标设备")
        check(repo.getDeviceHidConnectionState().value.isConnectedTo(target)) { "所选设备尚未连接" }
        boundTarget = target
        queue = Channel(16)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO); scope = s
        try {
            http = AutomationHttpServer(this, config.httpPort).also { it.start(10000, false) }
            running.value = true
            s.launch {
                for(work in queue) {
                    val task = synchronized(commandLock) {
                        if(work.epoch != epoch.get()) null else async {
                            try { withTimeout(if(cameraMode.value)120_000L else 45_000L) { engine.execute(work.raw) } }
                            catch (_: TimeoutCancellationException) { CommandEngine.response(idOf(work.raw),"FAILED","TIMEOUT","执行超时，可能已部分执行") }
                            catch (_: CancellationException) { CommandEngine.response(idOf(work.raw),"FAILED","CANCELLED","已停止，可能已部分执行") }
                        }.also { active=it }
                    }
                    if(task==null) {
                        synchronized(commandLock) { pendingCommands-- }
                        work.result.complete(CommandEngine.response(idOf(work.raw),"FAILED","CANCELLED","命令已停止"));continue
                    }
                    val result = try { task.await() } catch(_: CancellationException) { CommandEngine.response(idOf(work.raw),"FAILED","CANCELLED","已停止") }
                    synchronized(commandLock) { if(active===task) active=null; pendingCommands-- }; work.result.complete(result)
                    val delivery=result["data"]?.jsonObject?.stringValue("delivery", "") ?: ""
                    record("${actionOf(work.raw)} · ${result["status"]?.jsonPrimitive?.content}" + if(delivery.isBlank()) "" else " · $delivery")
                }
            }
            s.launch {
                repo.getDeviceHidConnectionState().collect { connection ->
                    if (!connection.isConnectedTo(target)) {
                        record("蓝牙连接已改变，自动化已停止")
                        stop()
                    }
                }
            }
            reconnectMqtt()
            record("自动化已启动 · HTTP ${config.httpPort}")
        } catch(e: Exception) {
            running.value = false; http?.stop(); http = null
            stoppingJob = s.coroutineContext[Job]; s.cancel(); scope = null; throw e
        }
    }
    @Synchronized fun stop() {
        val wasRunning = running.value
        authorizedTarget = null
        scope?.coroutineContext?.get(Job)?.let { stoppingJob = it }
        running.value = false; epoch.incrementAndGet(); active?.cancel(); goalLoop.cancel(); vision.cancel(); capture.stop(); companion.stop(); camera.stop(notify=false)
        http?.stop(); http = null; mqtt?.stop(); mqtt = null
        queue.close()
        while(true) {
            val work = queue.tryReceive().getOrNull() ?: break
            synchronized(commandLock) { pendingCommands-- }
            work.result.complete(CommandEngine.response(idOf(work.raw),"FAILED","CANCELLED","服务已停止"))
        }
        scope?.cancel(); scope = null; engine.releaseAll(); mqttState.value = "已停止"
        if (wasRunning) record("自动化已停止")
    }
    fun startGoal(goal: String, limits: GoalLimits = GoalLimits()) = synchronized(commandLock) {
        check(running.value && scope?.isActive == true) { "请先开启自动化" }
        check(engineConnected()) { "所选被控端已断开，请在手机重新连接" }
        check(vision.configured.value) { "请先配置Ollama云端密钥" }
        check(pendingCommands == 0 && !vision.working.value && !capture.capturing) { "请等待当前截图、识别或操作结束" }
        check(!goalLoop.active) { "目标任务尚未结束" }
        if(cameraMode.value)check(camera.open.value && camera.ready.value && !camera.busy.value) { "请先在设备页开启摄像头并完成画面与蓝牙落点校准" }
        goalUsesEnhancedControl = usesEnhancedControl()
        goalLoop.start(scope!!, goal, limits)
    }
    suspend fun cancelCommands() {
        val job = synchronized(commandLock) { epoch.incrementAndGet(); active?.also { it.cancel() } }
        job?.join()
        engine.releaseAll()
    }
    suspend fun submit(raw: String): JsonObject = submitCommand(raw, false)
    internal suspend fun submitGoalCommand(raw: String): JsonObject = submitCommand(raw, true)
    internal fun updateHttpConfiguration(input: JsonObject): AutomationConfig {
        val (previous, next) = synchronized(commandLock) {
            check(running.value && scope?.isActive == true) { "自动化服务已停止" }
            if (goalLoop.active) throw ActiveGoalConfigurationException()
            check(pendingCommands == 0 && !engine.busy && !vision.working.value && !capture.capturing) {
                "请等待当前截图、识别或操作结束"
            }
            require(!input.containsKey("token") && !input.containsKey("captureToken")) { "令牌只能在手机上管理" }
            val current = store.state.value
            val candidate = current.merge(input, true)
            require(candidate.httpPort == current.httpPort) { "修改端口请先在手机上停止服务" }
            // Share the goal-start gate through the small durable commit; network reconnect stays outside it.
            store.save(candidate)
            current to candidate
        }
        if (next.mqttUri != previous.mqttUri || next.mqttUsername != previous.mqttUsername ||
            next.mqttPassword != previous.mqttPassword || next.caCert != previous.caCert ||
            next.topicPrefix != previous.topicPrefix || next.deviceId != previous.deviceId) reconnectMqtt()
        return next
    }
    private suspend fun submitCommand(raw: String, fromGoal: Boolean): JsonObject {
        if(!running.value) return CommandEngine.response(idOf(raw),"FAILED","STOPPED","服务未启动")
        if(raw.toByteArray().size > 16384) return CommandEngine.response("","FAILED","TOO_LARGE","命令超过16KiB")
        when(actionOf(raw)) {
            "AI_START", "AI_STOP" -> {
                if (fromGoal) return CommandEngine.response(idOf(raw), "FAILED", "INVALID_ACTION", "任务不能启动其他任务")
                return remoteGoals.submit(raw)
            }
            "CANCEL", "RELEASE_ALL" -> {
                if(cameraMode.value && camera.calibrationStep.value>=0)camera.stop("落点校准已取消")
                goalLoop.stopAndAwait()
                vision.cancel()
                cancelCommands()
                return CommandEngine.response(idOf(raw),"SUCCESS", data = buildJsonObject { put("cancelled",true) })
            }
            "RECONNECT" -> { scope?.launch { delay(500); reconnectMqtt() }; return CommandEngine.response(idOf(raw),"SUCCESS") }
            "CONFIG" -> return CommandEngine.response(idOf(raw),"SUCCESS",data = buildJsonObject { put("url",addresses().firstOrNull() ?: ""); put("authenticationRequired",true) })
        }
        val deferred = CompletableDeferred<JsonObject>()
        synchronized(commandLock) {
            if (!running.value) return CommandEngine.response(idOf(raw),"FAILED","STOPPED","服务未启动")
            val readonly = actionOf(raw) in setOf("STATUS", "GET_STATUS", "GET_CONFIG")
            if(cameraMode.value && !readonly && (!camera.ready.value || camera.busy.value)) return CommandEngine.response(idOf(raw),"FAILED","CAMERA_NOT_READY","请先在主控前台完成摄像头与蓝牙落点校准")
            if (goalLoop.active && !fromGoal && !readonly) return CommandEngine.response(idOf(raw),"FAILED","AI_TASK_ACTIVE","目标任务执行中，请先停止任务再手动操作")
            if (fromGoal && !goalLoop.active) return CommandEngine.response(idOf(raw),"FAILED","CANCELLED","目标任务已结束")
            if (!readonly) actionGeneration.incrementAndGet()
            if(!queue.trySend(Work(raw,deferred,epoch.get())).isSuccess) return CommandEngine.response(idOf(raw),"FAILED","BUSY","队列已满")
            pendingCommands++
        }
        return deferred.await()
    }
    @Synchronized fun reconnectMqtt() {
        mqtt?.stop(); mqtt = null
        val s = scope ?: return
        val c = store.state.value
        if(c.mqttUri.isBlank()) { mqttState.value = "未配置（局域网可用）"; return }
        mqtt = AutomationMqtt(this,c,s).also { it.start() }
    }
    @Synchronized fun record(message: String) {
        val entry = java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(java.util.Date()) + " · " + message
        logs.value = (listOf(entry) + logs.value).take(50)
        mqtt?.log(entry)
    }
    fun status(): JsonObject = buildJsonObject {
        val state = repo.getDeviceHidConnectionState().value
        put("device_id",store.state.value.deviceId); put("running",running.value)
        put("status",if(engine.busy) "busy" else if(state.state == BluetoothProfile.STATE_CONNECTED) "connected" else "offline")
        put("bluetoothConnected",state.state == BluetoothProfile.STATE_CONNECTED); put("target",state.deviceName)
        put("mqtt",mqttState.value); put("battery",context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        put("batterySource","controller_a"); put("mode",store.state.value.mode); put("transport","Bluetooth Classic HID")
        put("timestamp",System.currentTimeMillis()); put("capture",capture.status())
        put("enhancedControl",companion.status())
        putJsonObject("camera") { put("selected",cameraMode.value);put("open",camera.open.value);put("ready",camera.ready.value) }
        put("remoteAi", 1); put("modelConfigured", vision.configured.value)
        put("aiTask", goalLoop.state.value.json())
    }
    fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }.map { "http://${it.hostAddress}:${store.state.value.httpPort}" }
    }.getOrDefault(emptyList())
    companion object {
        @Volatile private var instance: AutomationRuntime? = null
        fun get(context: Context): AutomationRuntime = instance ?: synchronized(this) { instance ?: AutomationRuntime(context.applicationContext).also { instance=it } }
        fun idOf(raw: String) = runCatching { Json.parseToJsonElement(raw).jsonObject.let { it.stringValue("id",it.stringValue("message_id","")) } }.getOrDefault("")
        fun actionOf(raw: String) = runCatching { Json.parseToJsonElement(raw).jsonObject.stringValue("action").uppercase(Locale.ROOT) }.getOrDefault("INVALID")
    }
}
