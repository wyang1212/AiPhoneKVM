// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import android.app.*
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.util.Base64
import android.view.WindowManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CaptureService: Service() {
    private var projection: MediaProjection?=null
    private var display: VirtualDisplay?=null
    private var contentWidth=0
    private var contentHeight=0
    private var reader: ImageReader?=null
    private val worker=Executors.newSingleThreadExecutor()
    private var imageThread: HandlerThread?=null
    private var latest: Bitmap?=null
    private var latestDisplay: JSONObject?=null
    private val stability=DisplayStability()
    private fun observeDisplay(): JSONObject = DisplaySnapshot.read(this).also {
        stability.observe(DisplaySnapshot.signature(it),SystemClock.elapsedRealtime())
    }
    private var lastFrameTime=0L
    private var lastProcessed=0L
    private var frameWidth=0
    private var frameHeight=0
    private val displayListener=object: DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) {
            if(id!=android.view.Display.DEFAULT_DISPLAY || !alive) return
            observeDisplay()
        }
    }
    private val transport=CaptureTransport()
    private var control: CompanionControlClient?=null
    @Volatile private var frameWaiter: java.util.concurrent.CountDownLatch?=null
    private val imageLock=Any()
    @Volatile private var alive=false
    private var address="";private var token=""
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="STOP") { stopSelf();return START_NOT_STICKY }
        if(alive) return START_NOT_STICKY
        val consent=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("consent",Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("consent")
        if(consent==null) { stopSelf();return START_NOT_STICKY }
        address=intent!!.getStringExtra("address") ?: "";token=intent.getStringExtra("token") ?: ""
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture","被控端屏幕共享",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,1,Intent(this,CaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AiPhoneKVM 正在共享被控端屏幕").setContentText("仅向配对的主控端回传截图，点击停止可结束")
            .setOngoing(true).addAction(Notification.Action.Builder(null,"停止共享",stop).build()).build()
        startForeground(30,notification)
        try {
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_OK),consent)
            imageThread=HandlerThread("capture-images").apply { start() }
            val handler=Handler(imageThread!!.looper)
            projection!!.registerCallback(object: MediaProjection.Callback() {
                override fun onStop() { state="屏幕共享已被系统停止";stopSelf() }
                override fun onCapturedContentResize(width: Int,height: Int) {
                    if(width>0 && height>0) { contentWidth=width;contentHeight=height;observeDisplay() }
                }
            },handler)
            val wm=getSystemService(WindowManager::class.java)
            val bounds=if(Build.VERSION.SDK_INT>=30) wm.maximumWindowMetrics.bounds else @Suppress("DEPRECATION") android.util.DisplayMetrics().let { wm.defaultDisplay.getRealMetrics(it);Rect(0,0,it.widthPixels,it.heightPixels) }
            val width=bounds.width();val height=bounds.height()
            require(width in 100..4096 && height in 100..4096)
            contentWidth=width;contentHeight=height
            observeDisplay()
            resize(width,height)
            getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener,handler)
            alive=true;running=true;state="已授权，等待主控端请求截图（$width × $height）"
            worker.submit { poll() }
            control=CompanionControlClient(this,address,token).also { it.start() }
        } catch(e: Exception) { state="启动失败：${e.javaClass.simpleName}";stopSelf() }
        return START_NOT_STICKY
    }
    private fun resize(width: Int,height: Int,force: Boolean=false) {
        if(!force && width==frameWidth && height==frameHeight) return
        require(width in 100..4096 && height in 100..4096)
        val handler=Handler(imageThread!!.looper)
        val next=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
        next.setOnImageAvailableListener({ source ->
                val image=runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                image.use {
                    val now=System.currentTimeMillis()
                    if(now-lastProcessed<250) return@setOnImageAvailableListener
                    lastProcessed=now
                    val plane=image.planes[0];val stride=plane.pixelStride;val padding=plane.rowStride-stride*image.width
                    val padded=Bitmap.createBitmap(image.width+padding/stride,image.height,Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap=Bitmap.createBitmap(padded,0,0,image.width,image.height)
                    if(bitmap!==padded)padded.recycle()
                    val geometry=observeDisplay()
                    geometry.put("fullDisplay",geometry.getInt("width")==bitmap.width && geometry.getInt("height")==bitmap.height)
                    synchronized(imageLock) { latest?.recycle();latest=bitmap;latestDisplay=geometry;lastFrameTime=now }
                    frameWaiter?.countDown();frameWaiter=null
                }
            },handler)
        val previous=reader
        if(display==null) {
            display=projection!!.createVirtualDisplay("AiPhoneKVM-B",width,height,resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,next.surface,null,handler)
        } else {
            // Keep the same projection/display. This ROM does not dispatch onPaused for
            // setSurface(null); leave the consumer detached across display traversal instead.
            display!!.surface=null
            display!!.resize(width,height,resources.configuration.densityDpi)
            handler.postDelayed({
                if(alive && reader===next) display?.surface=next.surface
            },150)
        }
        reader=next;frameWidth=width;frameHeight=height
        previous?.setOnImageAvailableListener(null,null);previous?.close()
        synchronized(imageLock) { latest?.recycle();latest=null;latestDisplay=null;lastFrameTime=0;lastProcessed=0 }
    }
    private fun request(path: String,body: JSONObject?=null): Pair<Int,String> {
        android.util.Log.d("AiPhoneCapture","request $path start")
        val response=transport.request(address,token,path,body?.toString())
        android.util.Log.d("AiPhoneCapture","response $path code=${response.first} chars=${response.second.length}")
        return response
    }
    private fun freshFrame(id: String): JSONObject {
        val result=JSONObject().put("id",id)
        repeat(3) {
            observeDisplay()
            val quiet=stability.remaining(SystemClock.elapsedRealtime())
            if(quiet>0) Thread.sleep(quiet)
            val latch=java.util.concurrent.CountDownLatch(1)
            Handler(imageThread!!.looper).post {
                frameWaiter=latch
                try {
                    val geometry=observeDisplay()
                    val width=if(Build.VERSION.SDK_INT<34) geometry.getInt("width") else contentWidth
                    val height=if(Build.VERSION.SDK_INT<34) geometry.getInt("height") else contentHeight
                    resize(width,height,true)
                }
                catch(_: Exception) { latch.countDown() }
            }
            val refreshed=latch.await(5000,java.util.concurrent.TimeUnit.MILLISECONDS)
            val current=observeDisplay()
            synchronized(imageLock) {
                val image=latest;val geometry=latestDisplay
                val stable=geometry!=null && DisplaySnapshot.signature(geometry)==DisplaySnapshot.signature(current) &&
                    stability.stable(DisplaySnapshot.signature(geometry),SystemClock.elapsedRealtime())
                if(image!=null && refreshed && stable) {
                    val age=(System.currentTimeMillis()-lastFrameTime).coerceAtLeast(0)
                    val bytes=ByteArrayOutputStream().use { out -> image.compress(Bitmap.CompressFormat.JPEG,80,out);out.toByteArray() }
                    return result.put("image",Base64.encodeToString(bytes,Base64.NO_WRAP))
                        .put("captured_at",lastFrameTime).put("age_ms",age).put("display",geometry)
                }
            }
        }
        return result.put("error","屏幕正在切换或尚未收到稳定画面，请稍后重试")
    }
    private fun poll() {
        while(alive) {
            try {
                val (code,text)=request("/api/capture/next")
                if(code==401) { state="配对码错误，请停止并重新填写";Thread.sleep(3000);continue }
                if(code==204) { state="已连接主控端，等待截图请求";continue }
                if(code!=200) throw IllegalStateException("HTTP $code")
                val id=JSONObject(text).getString("id")
                android.util.Log.d("AiPhoneCapture","received capture request")
                val result=freshFrame(id)
                val sent=request("/api/capture/frame",result)
                state=if(sent.first==200) "截图已回传主控端 · ${java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date())}" else "回传失败 HTTP ${sent.first}"
            } catch(_: InterruptedException) { break }
            catch(e: Exception) { if(alive) { android.util.Log.w("AiPhoneCapture","transport retry: ${e.javaClass.simpleName}: ${e.message?.replace(token, "[REDACTED]")?.take(140)}");state="连接重试：${e.javaClass.simpleName}（检查地址、Wi-Fi和主控端服务）";try { Thread.sleep(2000) }catch(_: InterruptedException){break} } }
        }
    }
    override fun onDestroy() {
        alive=false;running=false;control?.stop();control=null;transport.close();worker.shutdownNow()
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        display?.release();reader?.setOnImageAvailableListener(null,null);reader?.close();projection?.stop();imageThread?.quitSafely()
        synchronized(imageLock) { latest?.recycle();latest=null }
        state="屏幕共享已停止";super.onDestroy()
    }
    companion object { @Volatile var state="尚未开启屏幕共享";@Volatile var running=false }
}
