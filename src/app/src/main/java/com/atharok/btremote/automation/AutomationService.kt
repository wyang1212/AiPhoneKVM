// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atharok.btremote.R
import com.atharok.btremote.presentation.activities.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

class AutomationService: Service() {
    private val lifecycleScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var monitor: kotlinx.coroutines.Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = AutomationRuntime.get(this)
        if(intent?.action == "STOP") { runtime.stop();stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("automation","AiPhoneKVM 自动化",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this,1,Intent(this,AutomationService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        startForeground(20,NotificationCompat.Builder(this,"automation").setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("AiPhoneKVM 自动化运行中").setContentText("局域网 / MQTT → 蓝牙；点击停止关闭控制入口")
            .setContentIntent(open).setOngoing(true).addAction(0,"停止",stop).build())
        try {
            runtime.start()
            monitor?.cancel()
            monitor = lifecycleScope.launch { runtime.running.first { !it }; stopSelf(startId) }
        } catch(e: Exception) { runtime.record("启动失败：${e.javaClass.simpleName}（检查权限/端口）");stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { lifecycleScope.cancel();AutomationRuntime.get(this).stop();super.onDestroy() }
}
