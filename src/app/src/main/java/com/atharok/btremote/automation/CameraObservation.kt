// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sign

/** Foreground-only optical observation. No target companion, gallery or stored photos are involved. */
class CameraObservation(
    private val context: Context,
    private val mouse: (Int,Int)->Unit,
    private val onLost: (String)->Unit,
    private val canConfigure: ()->Boolean = { true }
) {
    data class Raw(val bytes:ByteArray,val width:Int,val height:Int,val sequence:Long,val elapsed:Long,val rotation:Int)
    val raw=MutableStateFlow<Raw?>(null)
    val open=MutableStateFlow(false)
    val ready=MutableStateFlow(false)
    val message=MutableStateFlow("摄像头未开启")
    val quad=MutableStateFlow<CameraQuad?>(null)
    val calibrationImage=MutableStateFlow<CaptureHub.Frame?>(null)
    val calibrationStep=MutableStateFlow(-1)
    val busy=MutableStateFlow(false)
    var width=720; private set
    var height=1600; private set
    @Volatile var revision=0L; private set
    private var pointerMap:CameraPointerMap?=null
    private val cursorPoints=mutableListOf<CameraPoint>()
    private val movement=Mutex()
    private var thread:HandlerThread?=null
    private var device:CameraDevice?=null
    private var session:CameraCaptureSession?=null
    private var reader:ImageReader?=null
    private var orientation:()->Int={0}
    private var lastPublished=0L
    private var sequence=0L
    private var imageRotation:Int?=null
    @Volatile private var epoch=0L

    fun coordinateConfig(config:AutomationConfig)=config.copy(screenWidth=width,screenHeight=height)
    fun requestReframe() { check(canConfigure() && !busy.value) { "请先停止当前任务或操作" };clearCalibration() }
    fun landscape(value:Boolean) {
        check(canConfigure()) { "请先停止当前任务或操作" }
        check(!busy.value) { "请等待校准结束" }
        width=if(value)1600 else 720;height=if(value)720 else 1600
        clearCalibration("屏幕方向已变，请重新框选")
    }
    fun clearCalibration(reason:String="请重新框选受控屏幕") {
        revision++;ready.value=false;quad.value=null;pointerMap=null;cursorPoints.clear()
        calibrationStep.value=-1;calibrationImage.value=null;message.value=reason
    }
    fun chooseScreen(value:CameraQuad) {
        check(canConfigure() && open.value && !busy.value) { "请先开启摄像头并等待当前操作结束" }
        clearCalibration("屏幕已框选，请校准蓝牙鼠标落点")
        val candidate=value.validate()
        val sample=raw.value?:error("请等待实时相机画面")
        check(candidate.sufficientlyDetailed(sample.width,sample.height,width>height)) { "受控屏幕入镜像素太少，请靠近后重新框选" }
        quad.value=candidate
    }
    @SuppressLint("MissingPermission")
    fun start(rotation:()->Int) {
        if(open.value)return
        stop(notify=false)
        orientation=rotation;val token=++epoch
        val manager=context.getSystemService(CameraManager::class.java)
        try {
            val id=manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK }
                ?:error("没有可用后置摄像头")
            val c=manager.getCameraCharacteristics(id)
            val sensor=c.get(CameraCharacteristics.SENSOR_ORIENTATION)?:0
            val sizes=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            val size=sizes.filter { it.width<=1600 && it.height<=1200 }.maxByOrNull { it.width*it.height }
                ?:sizes.minByOrNull { it.width*it.height }?:error("摄像头不支持图像采集")
            val handlerThread=HandlerThread("optical-observation").also { it.start();thread=it }
            val handler=Handler(handlerThread.looper)
            val images=ImageReader.newInstance(size.width,size.height,ImageFormat.YUV_420_888,2).also { reader=it }
            images.setOnImageAvailableListener({ r ->
                val image=runCatching { r.acquireLatestImage() }.getOrNull()?:return@setOnImageAvailableListener
                try {
                    val now=SystemClock.elapsedRealtime()
                    if(token!=epoch || now-lastPublished<450)return@setOnImageAvailableListener
                    lastPublished=now
                    val turn=(sensor-orientation()+360)%360
                    if(imageRotation!=null && imageRotation!=turn) {
                        clearCalibration("主控方向改变，请重新框选与校准")
                        onLost("主控方向改变，任务已停止")
                    }
                    imageRotation=turn
                    val w=image.width;val h=image.height;val nv21=ByteArray(w*h*3/2)
                    for(i in 0..2) {
                        val plane=image.planes[i];val b=plane.buffer;val pw=if(i==0)w else w/2;val ph=if(i==0)h else h/2
                        for(y in 0 until ph)for(x in 0 until pw) {
                            val value=b.get(y*plane.rowStride+x*plane.pixelStride)
                            if(i==0)nv21[y*w+x]=value else nv21[w*h+y*w+x*2+if(i==2)0 else 1]=value
                        }
                    }
                    val jpeg=ByteArrayOutputStream();check(YuvImage(nv21,ImageFormat.NV21,w,h,null).compressToJpeg(Rect(0,0,w,h),82,jpeg))
                    val decoded=BitmapFactory.decodeByteArray(jpeg.toByteArray(),0,jpeg.size())?:error("相机图像解码失败")
                    val bitmap=if(turn==0)decoded else Bitmap.createBitmap(decoded,0,0,w,h,Matrix().apply { postRotate(turn.toFloat()) },true)
                    val output=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,output)
                    if(token==epoch)raw.value=Raw(output.toByteArray(),bitmap.width,bitmap.height,++sequence,now,turn)
                    if(bitmap!==decoded)bitmap.recycle();decoded.recycle()
                } catch(e:Exception) { if(token==epoch)message.value="相机图像处理失败：${e.message?.take(80)}" }
                finally { image.close() }
            },handler)
            open.value=true;message.value="正在打开后置摄像头…"
            manager.openCamera(id,context.mainExecutor,object:CameraDevice.StateCallback() {
                override fun onOpened(camera:CameraDevice) {
                    if(token!=epoch) { camera.close();return };device=camera
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(images.surface),object:CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s:CameraCaptureSession) {
                            if(token!=epoch) { s.close();return };session=s
                            try {
                                val request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(images.surface)
                                    set(CaptureRequest.CONTROL_AF_MODE,if(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)==true)CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE else CaptureRequest.CONTROL_AF_MODE_OFF)
                                    set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
                                }.build()
                                s.setRepeatingRequest(request,null,handler);message.value="对准完整受控屏幕，固定两台手机后框选四角"
                            } catch(e:Exception) { lost("相机启动失败：${e.message?.take(80)}") }
                        }
                        override fun onConfigureFailed(s:CameraCaptureSession) { if(token==epoch)lost("相机图像流配置失败") }
                    },handler)
                }
                override fun onDisconnected(camera:CameraDevice) { camera.close();if(token==epoch)lost("摄像头已断开") }
                override fun onError(camera:CameraDevice,error:Int) { camera.close();if(token==epoch)lost("摄像头不可用（$error）") }
            })
        } catch(e:Exception) { lost("无法打开摄像头：${e.message?.take(100)}") }
    }
    private fun lost(reason:String) { stop(reason,true) }
    fun stop(reason:String="摄像头已关闭",notify:Boolean=true) {
        epoch++;open.value=false;ready.value=false;revision++
        runCatching { session?.stopRepeating() };session?.close();session=null
        device?.close();device=null;reader?.close();reader=null
        thread?.quitSafely();thread=null
        raw.value=null;imageRotation=null;lastPublished=0
        clearCalibration(reason)
        if(notify)onLost(reason)
    }
    private suspend fun fresh():Raw {
        check(open.value) { "请在主控前台开启摄像头" }
        val sequence=raw.value?.sequence?:-1
        val requestedAt=SystemClock.elapsedRealtime()
        return withTimeout(6000) { raw.filterNotNull().first { it.sequence>sequence && it.elapsed>=requestedAt && SystemClock.elapsedRealtime()-it.elapsed in 0..1500 } }
    }
    suspend fun capture():CaptureHub.Frame {
        val q=quad.value?:error("请先框选完整受控屏幕")
        val revision=revision;val source=fresh()
        check(open.value && this.revision==revision) { "相机或校准已变化，请重新拍摄" }
        val bitmap=BitmapFactory.decodeByteArray(source.bytes,0,source.bytes.size)?:error("相机图像无效")
        val rectified=createBitmap(width,height)
        val src=q.points.flatMap { listOf(it.x*bitmap.width,it.y*bitmap.height) }.toFloatArray()
        val matrix=Matrix();check(matrix.setPolyToPoly(src,0,floatArrayOf(0f,0f,width.toFloat(),0f,width.toFloat(),height.toFloat(),0f,height.toFloat()),0,4)) { "屏幕透视校准无效" }
        Canvas(rectified).apply { drawColor(Color.BLACK);drawBitmap(bitmap,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
        val output=ByteArrayOutputStream();rectified.compress(Bitmap.CompressFormat.JPEG,88,output)
        bitmap.recycle();rectified.recycle()
        check(open.value && this.revision==revision) { "相机或校准已变化，请重新拍摄" }
        val now=System.currentTimeMillis()
        return CaptureHub.Frame(output.toByteArray(),now-(SystemClock.elapsedRealtime()-source.elapsed),now,width,height,source="camera",sourceRevision=revision)
    }
    private suspend fun reset() {
        repeat(20) { currentCoroutineContext().ensureActive();check(open.value) { "摄像头已关闭，校准停止" };mouse(-127,-127);delay(100) }
        delay(500)
    }
    private suspend fun move(dx:Int,dy:Int) {
        // Space reports beyond Bluetooth's observed delivery bursts, keeping host acceleration low.
        for((x,y) in listOf(dx to 0,0 to dy)) {
            var remaining=abs(x+y)
            while(remaining>0) {
                currentCoroutineContext().ensureActive();check(open.value) { "摄像头已关闭，操作停止" }
                val step=minOf(4,remaining)
                mouse(x.sign*step,y.sign*step);delay(100);remaining-=step
            }
            delay(350)
        }
    }
    suspend fun beginCalibration() = movement.withLock {
        check(canConfigure() && open.value && quad.value!=null) { "请先框选屏幕并停止其他操作" }
        ready.value=false;pointerMap=null;cursorPoints.clear();revision++;calibrationStep.value=0;busy.value=true
        try { reset();move(CameraPointerMap.BASE_UNITS,CameraPointerMap.BASE_UNITS);calibrationImage.value=capture();message.value="点击屏幕内鼠标箭头的尖端" }
        catch(e:Exception) { calibrationStep.value=-1;calibrationImage.value=null;message.value=e.message?:"校准失败";throw e }
        finally { busy.value=false }
    }
    suspend fun recordCursor(p:CameraPoint)=movement.withLock {
        check(canConfigure() && !busy.value && calibrationStep.value>=0 && open.value) { "请等待校准画面" }
        busy.value=true
        try {
            val step=calibrationStep.value
            if(step<3)cursorPoints.add(p)
            if(step==2)pointerMap=CameraPointerMap.fromInterior(cursorPoints[0],cursorPoints[1],cursorPoints[2])
            if(step>=3)check(CameraPointerMap.accurate(p,CameraPointerMap.CHECK_POINTS[step-3])) { "落点误差超过2.5%，请重新框选或校准" }
            if(step==4) {
                revision++;ready.value=true;calibrationStep.value=-1;calibrationImage.value=null
                message.value="摄像头与蓝牙落点校准完成；移动任一手机后须重新校准"
            } else {
                calibrationStep.value=step+1;reset()
                when(step+1) {
                    1 -> move(CameraPointerMap.BASE_UNITS+CameraPointerMap.PROBE_UNITS.toInt(),CameraPointerMap.BASE_UNITS)
                    2 -> move(CameraPointerMap.BASE_UNITS,CameraPointerMap.BASE_UNITS+CameraPointerMap.PROBE_UNITS.toInt())
                    else -> pointerMap!!.units(CameraPointerMap.CHECK_POINTS[step-2]).let { move(it.first,it.second) }
                }
                calibrationImage.value=capture()
                message.value=when(step+1) { 1->"点击向右移动后的鼠标箭头尖端";2->"点击向下移动后的鼠标箭头尖端";else->"点击鼠标箭头尖端，核验${if(step==2)"中心" else "右下"}落点" }
            }
        } catch(e:Exception) { ready.value=false;pointerMap=null;calibrationStep.value=-1;message.value=e.message?:"校准失败";throw e }
        finally { busy.value=false }
    }
    suspend fun position(x:Int,y:Int)=movement.withLock {
        check(open.value && ready.value) { "请先完成摄像头与蓝牙落点校准" }
        val revision=revision;val map=pointerMap?:error("落点校准已失效")
        val units=map.units(CameraPoint(x.toFloat()/width,y.toFloat()/height))
        reset();move(units.first,units.second)
        check(open.value && this.revision==revision && ready.value) { "相机校准已改变，操作已停止" }
    }
}
