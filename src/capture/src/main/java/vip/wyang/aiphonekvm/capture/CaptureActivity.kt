// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*
import android.text.InputType
import android.view.View
import android.widget.*
import java.net.URI

class CaptureActivity: Activity() {
    private lateinit var address: EditText
    private lateinit var token: EditText
    private lateinit var status: TextView
    private lateinit var enhanced: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val update = object: Runnable { override fun run() {
        status.text=CaptureService.state
        enhanced.text=if(EnhancedControlService.instance!=null) "增强控制已授权：支持真实滑动和直接文本写入" else "增强控制未授权：目前仅使用蓝牙键鼠"
        handler.postDelayed(this,1000)
    } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("capture_private",MODE_PRIVATE)
        val dp = resources.displayMetrics.density
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding((24*dp).toInt(),(40*dp).toInt(),(24*dp).toInt(),(30*dp).toInt()) }
        fun label(text: String,size: Float=16f) = TextView(this).apply { this.text=text;textSize=size;setPadding(0,14,0,14);col.addView(this) }
        label("AiPhoneKVM 截图助手",25f)
        label("安装在被控端\n截图回传主控端供AI识别；可选增强控制执行触屏手势与文本。")
        label("主控端的局域网地址")
        address=EditText(this).apply { hint="http://controller.example.invalid:8765";setSingleLine();inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI;setText(prefs.getString("address",""));col.addView(this) }
        label("配对码（在主控端上复制）")
        token=EditText(this).apply { setSingleLine();inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setText(prefs.getString("token",""));col.addView(this) }
        col.addView(Button(this).apply { text="授权屏幕共享并连接主控端";setOnClickListener {
            val url = address.text.toString().trim().trimEnd('/')
            val key = token.text.toString().trim()
            val valid=runCatching { val u=URI(url);u.scheme=="http" && !u.host.isNullOrBlank() && u.userInfo==null && u.path.isNullOrEmpty() && u.query==null && u.fragment==null && key.length>=24 }.getOrDefault(false)
            if(!valid) { Toast.makeText(this@CaptureActivity,"请填写有效的主控端地址和配对码",Toast.LENGTH_LONG).show();return@setOnClickListener }
            if(CaptureService.running) { Toast.makeText(this@CaptureActivity,"请先停止当前共享",Toast.LENGTH_SHORT).show();return@setOnClickListener }
            prefs.edit().putString("address",url).putString("token",key).apply()
            if(Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),2)
            val mgr=getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION") startActivityForResult((if(Build.VERSION.SDK_INT>=34) mgr.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()) else mgr.createScreenCaptureIntent()),1)
        } })
        col.addView(Button(this).apply { text="停止屏幕共享";setOnClickListener { stopService(Intent(this@CaptureActivity,CaptureService::class.java)) } })
        status=label(CaptureService.state)
        enhanced=label("增强控制状态")
        col.addView(Button(this).apply { text="开启增强控制（无障碍）";setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } })
        label("需要中文、混合文本和触屏滑动时，请在系统无障碍设置开启本应用的“增强控制”。增强操作仅在本页启动屏幕共享、连接配对主控端期间接收；停止共享即停止。不会更换你的默认输入法。",14f)
        label("两台手机需网络互通。主控端先开启自动化，再在本机确认系统屏幕共享授权。Android 14 及以上请选择整个屏幕，以便坐标与被控端屏幕对应。\n\n只有主控端请求时才上传 JPEG；图片传给配对的主控端。系统保护的敏感窗口可能显示黑屏。主控端是否上传到 AI 由主控端上的操作决定。\n\n停止共享或系统撤销授权后，不再截屏和上传。每次重新开启均使用新的系统授权。",14f)
        col.addView(Button(this).apply {
            text="开源声明"
            setOnClickListener { showOpenSourceNotices() }
        })
        scroll.addView(col);setContentView(scroll)
        if(Build.VERSION.SDK_INT>=30) window.decorView.setOnApplyWindowInsetsListener { view,insets -> val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets }
    }
    private fun showOpenSourceNotices() {
        val notice = listOf("HTTP-client-NOTICE.txt", "GPL-3.0.txt", "Apache-2.0.txt").joinToString("\n\n") { name ->
            assets.open("third_party/$name").bufferedReader().use { it.readText() }
        }
        val content = TextView(this).apply {
            text = notice
            textSize = 14f
            setTextIsSelectable(true)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        AlertDialog.Builder(this).setTitle("开源声明")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("关闭", null).show()
    }

    @Deprecated("Uses system capture consent result")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==1 && resultCode==RESULT_OK && data!=null) {
            val prefs=getSharedPreferences("capture_private",MODE_PRIVATE)
            startForegroundService(Intent(this,CaptureService::class.java).putExtra("consent",data).putExtra("resultCode",resultCode)
                .putExtra("address",prefs.getString("address","")).putExtra("token",prefs.getString("token","")))
        } else if(requestCode==1) { CaptureService.state="屏幕共享未授权" }
    }
    override fun onResume() { super.onResume();handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update);super.onPause() }
}
