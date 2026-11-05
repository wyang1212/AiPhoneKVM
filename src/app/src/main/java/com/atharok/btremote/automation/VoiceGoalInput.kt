// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Explicit, foreground-only dictation. This module never starts an automation task. */
@Composable
fun VoiceGoalInput(goal: String, enabled: Boolean, onGoal: (String) -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var listening by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var append by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    val currentGoal by rememberUpdatedState(goal)
    val currentEnabled by rememberUpdatedState(enabled)
    val updateGoal by rememberUpdatedState(onGoal)
    val busy by rememberUpdatedState(onBusy)
    fun close() {
        generation++; recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        listening = false; finishing = false; partial = ""; busy(false)
    }
    fun accept(candidates: List<String>?) {
        if (!currentEnabled) return
        val text = VoiceDraft.text(candidates)
        if (text == null) { message = "没有识别到有效文字，请重试"; return }
        try {
            updateGoal(VoiceDraft.merge(currentGoal,text,append))
            message = "已填入任务目标，请核对后开始执行"
        } catch (error: IllegalArgumentException) { message = error.message.orEmpty() }
    }
    val external = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false; busy(false)
        if (result.resultCode == Activity.RESULT_OK) accept(result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        else message = "语音输入已取消，原目标保留"
    }
    fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)
        .putExtra(RecognizerIntent.EXTRA_PROMPT,"请说出希望被控端完成的任务")
    fun begin() {
        if (!currentEnabled || listening) return
        message = ""; partial = ""; finishing = false
        val normal = SpeechRecognizer.isRecognitionAvailable(context)
        val offline = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!normal && !offline) {
            try { listening = true; busy(true); external.launch(intent()) }
            catch (_: Exception) { close(); message = "系统未提供可用语音识别，请在系统设置启用语音服务后重试；也可继续键盘输入" }
            return
        }
        try {
            val session = ++generation
            val engine = if (!normal && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = engine; listening = true; busy(true)
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (generation == session) message = "正在听，请说出任务目标…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if (generation == session) { finishing = true; message = "正在识别…" } }
                override fun onPartialResults(results: Bundle?) {
                    if (generation == session) partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().take(2000)
                }
                override fun onResults(results: Bundle?) {
                    if (generation != session) return
                    val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    close(); accept(candidates)
                }
                override fun onError(error: Int) {
                    if (generation != session) return
                    close()
                    message = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限未授予，请在应用权限中允许后重试"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，请靠近手机重新说一遍"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音服务网络不可用，请检查网络后重试"
                        SpeechRecognizer.ERROR_AUDIO -> "麦克风不可用，请检查是否被其他应用占用或被系统禁用"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "系统语音服务正忙，请稍后重试"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "系统语音服务尚不支持中文，请在系统设置启用中文语音识别"
                        else -> "语音识别失败（$error），请重试或使用键盘输入"
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            engine.startListening(intent())
        } catch (_: Exception) { close(); message = "语音服务启动失败，请检查系统语音服务和麦克风权限" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else message = "未允许麦克风，原目标保留；可在应用权限中开启或继续键盘输入"
    }
    fun launch(add: Boolean) {
        append = add
        // External recognition activities own their microphone permission.
        if (!SpeechRecognizer.isRecognitionAvailable(context) &&
            !(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))) begin()
        else if (ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recognizer != null) { close(); message = "语音输入已停止，原目标保留" }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); close() }
    }
    LaunchedEffect(enabled) { if (!enabled && recognizer != null) close() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (listening) {
                OutlinedButton(onClick = { close(); message = "语音输入已取消，原目标保留" }) { Text("取消语音") }
                if (recognizer != null) Button(onClick = { finishing = true; recognizer?.stopListening(); message = "正在识别…" },enabled = !finishing) { Text(if(finishing) "识别中…" else "说完了") }
            } else {
                OutlinedButton(onClick = { launch(false) },enabled = enabled) { Text(if (goal.isBlank()) "语音输入" else "语音替换") }
                if (goal.isNotBlank()) OutlinedButton(onClick = { launch(true) },enabled = enabled) { Text("语音追加") }
            }
        }
        if (partial.isNotBlank()) Text(partial,style = MaterialTheme.typography.bodyMedium)
        Text(message.ifEmpty { "中文语音转为可编辑目标，由系统语音服务识别，核对后再执行。" },style = MaterialTheme.typography.bodySmall)
    }
}
