// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.common.extensions.getActivity
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun CameraObservationPanel(runtime:AutomationRuntime,enabled:Boolean,running:Boolean) {
    val camera=runtime.camera
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val open by camera.open.collectAsStateWithLifecycle()
    val raw by camera.raw.collectAsStateWithLifecycle()
    val quad by camera.quad.collectAsStateWithLifecycle()
    val ready by camera.ready.collectAsStateWithLifecycle()
    val busy by camera.busy.collectAsStateWithLifecycle()
    val image by camera.calibrationImage.collectAsStateWithLifecycle()
    val step by camera.calibrationStep.collectAsStateWithLifecycle()
    val message by camera.message.collectAsStateWithLifecycle()
    var frozen by remember { mutableStateOf<CameraObservation.Raw?>(null) }
    var corners by remember { mutableStateOf<List<CameraPoint>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(open) {
        if (!open) {
            frozen = null
            corners = emptyList()
            error = ""
        }
    }
    fun start() {
        frozen = null
        corners = emptyList()
        error = ""
        val activity=context.getActivity()?:return
        @Suppress("DEPRECATION")
        camera.start { activity.windowManager.defaultDisplay.rotation*90 }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted)start() else error="请允许主控端摄像头权限；原截图模式仍可使用"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("主控摄像头观察",style=MaterialTheme.typography.titleMedium)
            Text("对准完整亮屏并固定两台手机。完成框选与落点校准后，回任务页开始。")
            Text("仅将框选屏幕区域发给模型；照片留在内存，离开前台会关闭相机。",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={
                    if(open)camera.stop() else if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)start()
                    else permission.launch(Manifest.permission.CAMERA)
                },enabled=enabled&&!busy) { Text(if(open) "关闭摄像头" else "开启摄像头") }
                if(open)OutlinedButton(onClick={
                    try { camera.requestReframe();corners=emptyList();frozen=raw;error="" }catch(e:Exception) { error=e.message?:"请等待当前操作结束" }
                },enabled=enabled&&!busy&&raw!=null) { Text(if(quad==null) "框选屏幕" else "重新框选") }
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            Text(message)
            if(open) {
                val picture=frozen?:raw
                if(picture!=null) {
                    if(frozen!=null)Text("依次点击：左上 → 右上 → 右下 → 左下（已选${corners.size}/4）")
                    CameraCalibrationImage(picture.bytes,corners,"相机屏幕框选") { point ->
                        if(enabled&&!busy&&frozen!=null) {
                            val next=corners+point;corners=next
                            if(next.size==4) {
                                try { check(raw?.rotation==frozen?.rotation) { "相机方向已变，请重新拍摄框选" };camera.chooseScreen(CameraQuad(next));frozen=null;error="" }
                                catch(e:Exception) { error=e.message?:"框选失败";corners=emptyList() }
                            }
                        }
                    }
                } else Text("正在等待实时画面…")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={try { camera.landscape(false);corners=emptyList();frozen=null }catch(e:Exception) { error=e.message?:"请等待当前操作结束" }},enabled=enabled&&!busy) { Text("受控屏幕竖屏") }
                    TextButton(onClick={try { camera.landscape(true);corners=emptyList();frozen=null }catch(e:Exception) { error=e.message?:"请等待当前操作结束" }},enabled=enabled&&!busy) { Text("受控屏幕横屏") }
                }
                if(quad!=null) {
                    Text("校准仅移动鼠标。按提示点箭头尖端，共5步；看不到箭头时先检查蓝牙连接。")
                    Button(onClick={scope.launch {
                        try { camera.beginCalibration();error="" }catch(e:Exception) { error=e.message?:"校准失败" }
                    }},enabled=enabled&&running&&!busy) { Text(if(ready) "重新校准蓝牙落点" else "开始蓝牙落点校准") }
                    if(image!=null&&step>=0) {
                        Text("第${step+1}/5步：${message}。请点箭头尖端。")
                        CameraCalibrationImage(image!!.bytes,emptyList(),"鼠标落点校准图") { p ->
                            if(enabled&&!busy)scope.launch {
                                try { camera.recordCursor(p);error="" }catch(e:Exception) { error=e.message?:"校准失败" }
                            }
                        }
                    }
                    if(!running)Text("请先开启自动化，再校准蓝牙落点。")
                }
            }
            Text(if(ready) "已就绪，可回任务页启动目标。手机移动或旋转后请重新校准。" else "未校准，自动执行不可用。",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CameraCalibrationImage(bytes:ByteArray,points:List<CameraPoint>,label:String,onPoint:(CameraPoint)->Unit) {
    val bitmap=remember(bytes) { BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap() }?:return
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentClick by rememberUpdatedState(onPoint)
    Box(Modifier.fillMaxWidth().height(360.dp).background(Color.Black)
        .semantics { contentDescription=label }.onSizeChanged { size=it }
        .pointerInput(bitmap) { detectTapGestures { p ->
            val scale=min(size.width.toFloat()/bitmap.width,size.height.toFloat()/bitmap.height)
            if(scale>0) {
                val x=(p.x-(size.width-bitmap.width*scale)/2)/(bitmap.width*scale)
                val y=(p.y-(size.height-bitmap.height*scale)/2)/(bitmap.height*scale)
                if(x in 0f..1f && y in 0f..1f)currentClick(CameraPoint(x,y))
            }
        } }) {
        Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val scale=min(size.width.toFloat()/bitmap.width,size.height.toFloat()/bitmap.height)
            points.forEach { p -> drawCircle(Color.Yellow,8.dp.toPx(),Offset((size.width-bitmap.width*scale)/2+p.x*bitmap.width*scale,(size.height-bitmap.height*scale)/2+p.y*bitmap.height*scale)) }
        }
    }
}
