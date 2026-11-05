// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit confirmation is required for both camera QR codes and verified HTTPS app links. */
class WebsitePairingActivity : ComponentActivity() {
    private var token by mutableStateOf<String?>(null)
    private var account by mutableStateOf("")
    private var retryLink by mutableStateOf<String?>(null)
    private var message by mutableStateOf("在网站点击添加主控端，然后扫描二维码。")
    private var working by mutableStateOf(false)
    private var complete by mutableStateOf(false)
    private lateinit var enrollment: WebsiteEnrollment
    private var inspectedOrigin = ""

    private fun loadLink(link: String) {
        if (working || enrollment.pending) return
        lifecycleScope.launch {
            working = true; token = null; complete = false; retryLink = null
            try {
                inspectedOrigin = enrollment.origin
                val parsed = pairingToken(link, inspectedOrigin)
                retryLink = link
                account = enrollment.inspect(parsed, inspectedOrigin); token = parsed
                message = "即将把本机加入以下网站账号，请核对后确认。"
            } catch (e: Exception) { message = enrollmentMessage(e) }
            finally { working = false }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(); enrollment = WebsiteEnrollment(this)
        setContent {
            MaterialTheme {
                var selectedOrigin by remember { mutableStateOf(enrollment.origin) }
                var address by rememberSaveable { mutableStateOf(selectedOrigin) }
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                val image = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        working = true
                        try { val link = withContext(Dispatchers.IO) { decodeImage(uri) }; working = false; loadLink(link) }
                        catch (e: Exception) { android.util.Log.w("WebsitePairing", "QR image read failed: ${e.javaClass.simpleName}"); working = false; message = "未识别到绑定二维码，请选择清晰、完整的二维码图片" }
                    }
                }
                val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
                    result.contents?.let(::loadLink) ?: run { message = "扫码未完成，可重新扫描或选择图片。" }
                }
                BackHandler(working) { /* Finish or recover the explicit transaction before leaving. */ }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("网站连接", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(address, { address = it }, label = { Text("网站地址（HTTPS）") },
                            singleLine = true, enabled = !working && !enrollment.pending, modifier = Modifier.fillMaxWidth())
                        Text("自建服务填写网站根地址，例如 https://control.example.com。保存后扫描该网站的二维码。")
                        OutlinedButton(enabled = !working && !enrollment.pending && address != selectedOrigin,
                            onClick = { lifecycleScope.launch {
                                working = true
                                try {
                                    enrollment.changeOrigin(address)
                                    selectedOrigin = enrollment.origin; address = selectedOrigin
                                    focus.clearFocus(); keyboard?.hide()
                                    token = null; account = ""; retryLink = null; complete = false
                                    message = "网站地址已保存。重新扫码绑定后替换原 MQTT 连接。"
                                } catch (e: Exception) { message = enrollmentMessage(e) }
                                finally { working = false }
                            } }) { Text("保存网站地址") }
                        Text("当前网站：$selectedOrigin", style = MaterialTheme.typography.bodySmall)
                        Text(message)
                        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (token != null && !complete && !enrollment.pending) {
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("绑定账号", style = MaterialTheme.typography.labelLarge)
                                Text(inspectedOrigin)
                                Text(account, style = MaterialTheme.typography.headlineSmall)
                                Text("本机：${android.os.Build.MODEL}")
                                Text("将停止当前控制并保存网站连接。蓝牙、AI模型和截图设置保留，不会启动AI任务。")
                            } }
                            Button(enabled = !working, modifier = Modifier.fillMaxWidth(), onClick = {
                                val current = token ?: return@Button
                                lifecycleScope.launch {
                                    working = true
                                    try {
                                        enrollment.bind(current, account, inspectedOrigin) { text -> runOnUiThread { message = text } }
                                        complete = true; token = null
                                        message = "绑定成功，MQTT加密连接已验证。请返回首页选择模式、被控端，再开启自动化。"
                                    } catch (e: Exception) { message = e.message?.take(220) ?: "配对未完成" }
                                    finally { working = false }
                                }
                            }) { Text("确认绑定并连接") }
                        }
                        if (!working && !complete && token == null && retryLink != null && !enrollment.pending) {
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { retryLink?.let(::loadLink) }) { Text("重新连接网站") }
                        }
                        if (enrollment.pending) {
                            Button(enabled = !working, modifier = Modifier.fillMaxWidth(), onClick = {
                                lifecycleScope.launch {
                                    working = true
                                    try {
                                        enrollment.recover { text -> runOnUiThread { message = text } }
                                        complete = true; token = null; message = "已确认绑定成功，连接配置已保存。"
                                    } catch (e: Exception) { message = e.message ?: "请联网后重试" }
                                    finally { working = false }
                                }
                            }) { Text("恢复上次配对") }
                        } else if (!complete) {
                            OutlinedButton(enabled = !working, modifier = Modifier.fillMaxWidth(), onClick = {
                                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("扫描设备控制台的绑定二维码").setBeepEnabled(false).setOrientationLocked(false))
                            }) { Text("扫描网站二维码") }
                            TextButton(enabled = !working, modifier = Modifier.fillMaxWidth(), onClick = { image.launch(arrayOf("image/*")) }) { Text("从图片识别二维码") }
                        }
                        TextButton(enabled = !working, onClick = { finish() }) { Text(if (complete) "完成" else "返回") }
                    }
                }
            }
        }
        if (enrollment.pending) message = "上次配对尚未确认，请联网后恢复，以免覆盖连接配置。"
        else intent?.let(::handleLinkIntent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!working && !enrollment.pending) handleLinkIntent(intent)
    }
    private fun handleLinkIntent(incoming: Intent) {
        val extra = incoming.getStringExtra("enrollment_token")
        val site = enrollment.origin
        val link = if (incoming.dataString == "$site/pair" && extra != null)
            "$site/pair#v=1&token=$extra" else incoming.dataString
        link?.let(::loadLink)
    }
    private fun decodeImage(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null,
            BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("图片不可读")
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
            return MultiFormatReader().decode(binary, mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)).text
        } finally { bitmap.recycle() }
    }
}

@Composable
fun WebsiteBindingRow() {
    val context = LocalContext.current
    val enrollment = remember { WebsiteEnrollment(context) }
    var revision by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { revision++; onPauseOrDispose { } }
    val boundAccount = remember(revision) { enrollment.account }
    val isPending = remember(revision) { enrollment.pending }
    val origin = remember(revision) { enrollment.origin }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("网站连接", style = MaterialTheme.typography.labelLarge)
            Text(origin, style = MaterialTheme.typography.bodySmall)
            Text(if (isPending) "配对待恢复" else boundAccount.ifBlank { "扫码后自动配置连接" }, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { context.startActivity(Intent(context, WebsitePairingActivity::class.java)) }) {
            Text(if (isPending) "恢复配对" else "网站设置 / 扫码")
        }
    }
}
