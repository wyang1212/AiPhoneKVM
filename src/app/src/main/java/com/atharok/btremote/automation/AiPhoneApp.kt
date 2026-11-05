// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import com.atharok.btremote.BuildConfig

import android.Manifest
import android.content.*
import android.os.Build
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalView
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.ui.app.BtRemoteApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

@Composable
fun AiPhoneApp() {
    val context = LocalContext.current
    val runtime = remember { AutomationRuntime.get(context) }
    BtRemoteApp(runtime = runtime, automationScreen = { connection, goHome, settings ->
        AiAutomationPage(runtime, connection, goHome, settings)
    })
}

@Composable
private fun AiAutomationPage(
    runtime: AutomationRuntime,
    connection: com.atharok.btremote.domain.entities.DeviceHidConnectionState,
    goHome: () -> Unit,
    settings: () -> Unit
) {
    val context = LocalContext.current
    var startupError by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(onBack = goHome)
    LaunchedEffect(connection.deviceAddress) {
        try {
            runtime.authorizeTarget(connection.deviceAddress)
            ContextCompat.startForegroundService(context, Intent(context, AutomationService::class.java))
        } catch (error: Exception) { startupError = error.message ?: "自动化启动失败" }
    }
    com.atharok.btremote.ui.components.AppScaffold(
        title = "AI 自动化 · ${connection.deviceName}",
        navigateUp = { com.atharok.btremote.ui.components.NavigateUpIconButton(goHome) },
        topBarActions = { com.atharok.btremote.ui.components.SettingsIconButton(settings) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (startupError.isNotBlank()) Text(startupError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            AutomationScreen(runtime, goHome, connection.deviceAddress)
        }
    }
}

@Composable
internal fun AutomationScreen(runtime: AutomationRuntime, goHome: () -> Unit, targetAddress: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by runtime.store.state.collectAsStateWithLifecycle()
    val running by runtime.running.collectAsStateWithLifecycle()
    val cameraMode by runtime.cameraMode.collectAsStateWithLifecycle()
    val cameraReady by runtime.camera.ready.collectAsStateWithLifecycle()
    val cameraOpen by runtime.camera.open.collectAsStateWithLifecycle()
    val cameraBusy by runtime.camera.busy.collectAsStateWithLifecycle()
    val owner=LocalLifecycleOwner.current
    val view=LocalView.current
    DisposableEffect(runtime,owner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP && runtime.camera.open.value)runtime.camera.stop("主控已离开前台，摄像头任务已停止")
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);runtime.camera.stop("已离开AI页面",notify=runtime.cameraMode.value && runtime.camera.open.value) }
    }
    DisposableEffect(view,cameraMode,cameraOpen) {
        val previous=view.keepScreenOn
        if(cameraMode&&cameraOpen)view.keepScreenOn=true
        onDispose { view.keepScreenOn=previous }
    }
    val mqtt by runtime.mqttState.collectAsStateWithLifecycle()
    val logs by runtime.logs.collectAsStateWithLifecycle()
    val frame by runtime.capture.lastFrame.collectAsStateWithLifecycle()
    val aiModel by runtime.vision.model.collectAsStateWithLifecycle()
    val aiUrl by runtime.vision.baseUrl.collectAsStateWithLifecycle()
    val aiConfigured by runtime.vision.configured.collectAsStateWithLifecycle()
    val aiWorking by runtime.vision.working.collectAsStateWithLifecycle()
    val aiResult by runtime.vision.result.collectAsStateWithLifecycle()
    val aiSuggestion by runtime.vision.suggestion.collectAsStateWithLifecycle()
    val task by runtime.goalLoop.state.collectAsStateWithLifecycle()
    val aiBusy = aiWorking || task.active
    val enhancedReady by produceState(false, runtime) {
        while(true) { value=runtime.usesEnhancedControl();delay(1000) }
    }
    var aiModelInput by remember(aiModel) { mutableStateOf(aiModel) }
    var aiUrlInput by remember(aiUrl) { mutableStateOf(aiUrl) }
    var aiKeyInput by remember { mutableStateOf("") }
    var aiGoal by rememberSaveable { mutableStateOf("") }
    var maxSteps by rememberSaveable { mutableStateOf("30") }
    var maxMinutes by rememberSaveable { mutableStateOf("15") }
    var message by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var advancedMqtt by remember { mutableStateOf(false) }
    var advancedCoordinates by remember { mutableStateOf(false) }
    var voiceBusy by remember { mutableStateOf(false) }
    val reportedScreen by runtime.companion.screen.collectAsStateWithLifecycle()
    var automaticScreen by remember { mutableStateOf<ControlledScreen?>(null) }
    LaunchedEffect(reportedScreen, runtime) {
        while(true) { automaticScreen=runtime.companion.currentScreen(); delay(1000) }
    }
    var stopping by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var width by remember(config.screenWidth) { mutableStateOf(config.screenWidth.toString()) }
    var height by remember(config.screenHeight) { mutableStateOf(config.screenHeight.toString()) }
    var sensitivity by remember(config.sensitivity) { mutableStateOf(config.sensitivity.toString()) }
    var uri by remember(config.mqttUri) { mutableStateOf(config.mqttUri) }
    var username by remember(config.mqttUsername) { mutableStateOf(config.mqttUsername) }
    var password by remember(config.mqttPassword) { mutableStateOf(config.mqttPassword) }
    var prefix by remember(config.topicPrefix) { mutableStateOf(config.topicPrefix) }
    var ca by remember(config.caCert) { mutableStateOf(config.caCert) }
    var port by remember(config.httpPort) { mutableStateOf(config.httpPort.toString()) }
    var command by remember { mutableStateOf("{\"action\":\"STATUS\",\"params\":{}}") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save(): Boolean = try {
        runtime.store.save(config.copy(screenWidth=width.toInt(),screenHeight=height.toInt(),sensitivity=sensitivity.toDouble(),
            mqttUri=uri.trim(),mqttUsername=username,mqttPassword=password,topicPrefix=if(prefix.endsWith('/')) prefix else "$prefix/",caCert=ca,httpPort=port.toInt()).validate())
        message="设置已保存";true
    } catch(e: Exception) { message=e.message ?: "设置无效";false }
    fun copy(value: String) { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AiPhoneKVM",value));message="已复制，请仅交给可信设备" }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showLimits by rememberSaveable { mutableStateOf(false) }
    var showSingleStep by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState(), rememberScrollState())
    LaunchedEffect(tab) { showToken = false }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(running) "自动化已开启" else "自动化未开启",style=MaterialTheme.typography.titleMedium)
                Text(if(cameraMode) "摄像头观察 · 蓝牙控制" else if(enhancedReady) "增强控制已连接" else "使用蓝牙键鼠",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=goHome) { Text("切换设备") }
                    Button(onClick={
                        if(running) {
                            stopping=true
                            scope.launch {
                                try { runtime.stopAndAwait();context.stopService(Intent(context,AutomationService::class.java)) }
                                finally { stopping=false }
                            }
                        }
                        else if(!checkBluetoothConnectPermission(context)) message="请先进入蓝牙页授予权限并连接被控端"
                        else if(save()) {
                            if(Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            runCatching {
                                runtime.authorizeTarget(targetAddress)
                                ContextCompat.startForegroundService(context,Intent(context,AutomationService::class.java))
                            }
                                .onFailure { message="启动失败，请检查蓝牙权限" }
                        }
                    },enabled=!stopping) { Text(if(stopping) "停止中…" else if(running) "停止自动化" else "开启自动化") }
                }
            }
        }
        }
        PrimaryTabRow(selectedTabIndex=tab) {
            listOf("任务", "设备", "设置", "网站").forEachIndexed { index, title ->
                Tab(selected=tab==index,onClick={tab=index},text={Text(title)})
            }
        }
        if(message.isNotBlank()) Text(message,Modifier.padding(horizontal=16.dp,vertical=4.dp),color=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollStates[tab]).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                0 -> {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("目标自动执行",style=MaterialTheme.typography.titleLarge)
                Text("输入目标或语音描述，点击开始后自动执行。离开 AI 模式会结束任务。")
                if(cameraMode) Text(if(cameraReady) "摄像头与蓝牙落点已校准" else "请先到设备页开启摄像头并完成校准")
                else if(!enhancedReady) Text("中文可先输入拼音再选择候选。需要增强触屏时，可在被控端助手授权。")
                OutlinedTextField(aiGoal,{aiGoal=it},label={Text("任务目标")},placeholder={Text("描述你希望被控端完成的事情")},modifier=Modifier.fillMaxWidth(),maxLines=4,enabled=!aiBusy&&!voiceBusy)
                VoiceGoalInput(aiGoal,enabled=!aiBusy,onGoal={aiGoal=it},onBusy={voiceBusy=it})
                TextButton(onClick={showLimits=!showLimits}) { Text("运行限制 · ${maxSteps}步 / ${maxMinutes}分钟 · ${if(showLimits) "收起" else "调整"}") }
                if(showLimits) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(maxSteps,{maxSteps=it},label={Text("最多操作步数")},singleLine=true,enabled=!aiBusy,modifier=Modifier.weight(1f))
                    OutlinedTextField(maxMinutes,{maxMinutes=it},label={Text("最长运行分钟")},singleLine=true,enabled=!aiBusy,modifier=Modifier.weight(1f))
                }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={
                        try { runtime.startGoal(aiGoal,GoalLimits(maxSteps.toInt(),maxMinutes.toInt()));message="目标任务已开始" }
                        catch(error: Exception) { message=error.message ?: "任务启动失败" }
                    },enabled=running&&aiConfigured&&!aiBusy&&!loading&&!voiceBusy&&aiGoal.isNotBlank()&&(!cameraMode||cameraReady)) { Text("开始自动执行") }
                    if(task.active) OutlinedButton(onClick={scope.launch { runtime.goalLoop.stopAndAwait() }},enabled=task.status!=GoalTaskStatus.STOPPING) { Text(if(task.status==GoalTaskStatus.STOPPING) "停止中…" else "停止任务") }
                }
                Text("${task.status.label} · 已操作 ${task.steps} 步 · 已识别 ${task.rounds} 轮")
                if(task.goal.isNotBlank() && task.goal != aiGoal) Text("当前目标：${task.goal}")
                if(task.summary.isNotBlank()) Text(task.summary)
                if(task.message.isNotBlank()) Text(task.message)
                if(!aiConfigured) TextButton(onClick={tab=2}) { Text("前往设置配置视觉模型") }
            }
        }
                    AutomationSection("屏幕预览") {
        Button(onClick={loading=true;scope.launch {
            message=try { withContext(Dispatchers.IO) { runtime.capture.capture() };if(cameraMode) "已拍摄受控屏幕" else "已收到被控端的截图" } catch(e: Exception) { "截图失败：${e.message?.take(120)}" }
            loading=false
        }},enabled=running&&!loading&&!aiBusy) { Text(if(loading) { if(cameraMode) "正在拍摄…" else "等待被控端回传…" } else if(cameraMode) "拍摄受控屏幕" else "获取被控端截图") }
        frame?.let { f ->
            val bitmap = remember(f) { BitmapFactory.decodeByteArray(f.bytes,0,f.bytes.size) }
            bitmap?.let { Image(it.asImageBitmap(),"被控端屏幕截图",Modifier.fillMaxWidth().heightIn(max=420.dp)) }
            Text("${f.width} × ${f.height} · 最近截图仅保存在主控端内存中")
        }
                    }
                    TextButton(onClick={showSingleStep=!showSingleStep}) { Text(if(showSingleStep) "收起单步操作" else "单步操作") }
                    if(showSingleStep) { AutomationSection("单步操作") {
        Text("单步识别使用当前任务目标，不启动循环。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={scope.launch {
                try { runtime.vision.analyze(aiGoal) } catch(_: kotlinx.coroutines.CancellationException) { } catch(e: Exception) { message=e.message ?: "识别失败" }
            }},enabled=running&&aiConfigured&&!aiBusy&&!voiceBusy&&aiGoal.isNotBlank()) { Text(if(aiWorking) "正在识别…" else "截图并让 AI 识别") }
            if(aiWorking&&!task.active) OutlinedButton(onClick={runtime.vision.cancel()}) { Text("停止识别") }
        }
        if(aiResult.isNotBlank()) SelectionContainer { Text(aiResult) }
        aiSuggestion?.let { suggestion ->
            SelectionContainer { Text(suggestion.toString()) }
            Button(onClick={scope.launch { message=try { runtime.vision.executeSuggestion().toString() } catch(e: Exception) { e.message ?: "执行失败" } }},enabled=running&&!aiBusy) { Text("执行 AI 建议的一步") }
        }
                    } }
                }
                1 -> {
                    AutomationSection("画面来源") {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=!cameraMode,onClick={try { runtime.selectCamera(false) }catch(e:Exception) { message=e.message?:"请先停止当前操作" }},enabled=!aiBusy&&!loading&&!cameraBusy,label={Text("截图助手")})
                            FilterChip(selected=cameraMode,onClick={try { runtime.selectCamera(true) }catch(e:Exception) { message=e.message?:"请先停止当前操作" }},enabled=!aiBusy&&!loading&&!cameraBusy,label={Text("主控摄像头")})
                        }
                        Text(if(cameraMode) "无需受控端软件；固定相机并完成校准后使用蓝牙键鼠。" else "受控端安装助手共享屏幕，支持增强触屏。")
                    }
                    if(cameraMode) CameraObservationPanel(runtime,!aiBusy&&!loading,running)
                    else {
                    AutomationSection("屏幕与控制") {
        automaticScreen?.let { screen ->
            Text("自动获取：${screen.manufacturer} ${screen.model} · ${screen.width} × ${screen.height} · ${if(screen.width>screen.height) "横屏" else "竖屏"}")
            Text(if(enhancedReady && screen.fullDisplay) "增强触屏使用整屏像素坐标，无需校准鼠标系数；屏幕旋转后自动更新。"
                else if(!screen.fullDisplay) "当前共享画面与整屏不一致，请在被控端重新共享整个屏幕。"
                else "屏幕尺寸已自动获取。开启被控端增强控制后，点击与滑动无需鼠标校准。")
        } ?: Text("等待被控端截图助手连接；连接后自动获取设备、屏幕宽高与方向。旧版助手请先更新。")
        OutlinedButton(onClick={advancedCoordinates=!advancedCoordinates},enabled=!aiBusy) {
            Text(if(advancedCoordinates) "收起蓝牙鼠标高级校准" else "蓝牙鼠标高级校准")
        }
        if(advancedCoordinates) {
            Text("仅纯蓝牙键鼠使用以下参数。增强触屏不使用鼠标系数；有助手上报时，自动屏幕尺寸优先。")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(width,{width=it},label={Text("备用屏幕宽")},modifier=Modifier.weight(1f),singleLine=true,enabled=!running)
                OutlinedTextField(height,{height=it},label={Text("备用屏幕高")},modifier=Modifier.weight(1f),singleLine=true,enabled=!running)
            }
            OutlinedTextField(sensitivity,{sensitivity=it},label={Text("像素 / HID 单位")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
            Text("蓝牙鼠标受系统加速影响，此系数不能由屏幕尺寸推算。建议使用被控端增强控制。")
        }
                        if(advancedCoordinates) Button(onClick={save()},enabled=!running) { Text("保存校准设置") }
                    }
                    AutomationSection("连接被控端") {
                        if(running) SelectionContainer { Text(runtime.addresses().joinToString("\n").ifEmpty { "暂无局域网地址，请连接Wi-Fi" }) }
        Text("在当前被控端的截图助手填写主控端地址与配对码，再确认屏幕共享。首次进入新版或更换控制设备后，被控端配对码会更新，请重新填写。")
        OutlinedButton(onClick={copy(config.captureToken)}) { Text("复制被控端配对码") }
                    }
                    }
                }
                2 -> {
                    AutomationSection("视觉模型") {
        Text("自动执行会将每轮被控端的新截图上传到此处配置的模型服务。也可单独截图识别，查看建议后手动执行一步。")
        OutlinedTextField(aiUrlInput,{aiUrlInput=it},label={Text("兼容模型接口地址")},singleLine=true,enabled=!aiBusy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(aiModelInput,{aiModelInput=it},label={Text("视觉模型")},singleLine=true,enabled=!aiBusy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(aiKeyInput,{aiKeyInput=it},label={Text(if(aiConfigured) "密钥已配置（留空保持）" else "模型 API 密钥")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!aiBusy,modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={
            try { runtime.vision.save(aiUrlInput,aiModelInput,aiKeyInput.takeIf { it.isNotBlank() });aiKeyInput="";message="模型设置已保存，密钥已加密保存在主控端" }
            catch(e: Exception) { message=e.message ?: "保存失败" }
        },enabled=!aiBusy) { Text("保存模型设置") }
                    }
                    AutomationSection("局域网与远程控制") {
        OutlinedTextField(port,{port=it},label={Text("HTTP端口")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
        Text("仅在可信局域网或 VPN 使用 HTTP。接口需访问令牌；停止自动化即关闭网络入口。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={copy(config.token)}) { Text("复制控制令牌") }
            TextButton(onClick={showToken=!showToken}) { Text(if(showToken) "隐藏" else "查看令牌") }
        }
        if(showToken) SelectionContainer { Text(config.token) }
        Text("网站远程连接可在“网站”标签设置地址并扫码绑定，无需填写MQTT参数。",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={advancedMqtt=!advancedMqtt},enabled=!task.active) { Text(if(advancedMqtt) "收起MQTT高级设置" else "MQTT高级设置") }
        if(advancedMqtt) {
        Text("MQTT 远程控制（可选）",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(uri,{uri=it},label={Text("ssl://主机:8883 或 tcp://主机:1883")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(username,{username=it},label={Text("MQTT用户名")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("MQTT密码")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(prefix,{prefix=it},label={Text("主题前缀")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(ca,{ca=it},label={Text("自定义 CA PEM（可留空使用系统信任）")},enabled=!running,modifier=Modifier.fillMaxWidth(),maxLines=4)
        SelectionContainer { Text("设备 ID：${config.deviceId}\n命令：${config.topicPrefix}command/${config.deviceId}\n结果 / 状态 / 日志：同前缀 response / status / log") }
        }
        Button(onClick={save()},enabled=!running) { Text("保存设置") }
                    }
                    TextButton(onClick={showDiagnostics=!showDiagnostics}) { Text(if(showDiagnostics) "收起调试与记录" else "调试与记录") }
                    if(showDiagnostics) { AutomationSection("调试与记录") {
        OutlinedTextField(command,{command=it},label={Text("BlueHIDFlow JSON 命令")},modifier=Modifier.fillMaxWidth(),maxLines=6)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={scope.launch { message=runtime.submit(command).toString() }},enabled=running&&!task.active) { Text("执行命令") }
            OutlinedButton(onClick={scope.launch { message=runtime.submit("{\"action\":\"CANCEL\"}").toString() }},enabled=running) { Text("停止并松键") }
        }
        Text("最近记录（不记录输入文本和凭据）",style=MaterialTheme.typography.titleMedium)
        logs.take(12).forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
                    } }
        Text("AiPhoneKVM ${BuildConfig.VERSION_NAME.removeSuffix("-debug")} 测试版 · GPL-3.0\n基于 PhoneKVM / Atharok BtRemote / jqssun android-bt-remote；命令协议参考 antgroup BlueHIDFlow。",style=MaterialTheme.typography.bodySmall)
                }
                3 -> {
                    AutomationSection("网站远程连接") {
                        WebsiteBindingRow()
                        Text("可使用默认网站，或在网站设置中保存自己部署的 HTTPS 网站地址，再扫描该网站生成的二维码。")
                        Text("MQTT：$mqtt")
                        Text("网站绑定会停止当前控制；绑定完成后可重新开启自动化。")
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
