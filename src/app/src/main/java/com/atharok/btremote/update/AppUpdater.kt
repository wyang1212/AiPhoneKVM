// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.update

import android.annotation.SuppressLint
import androidx.core.content.edit
import androidx.core.net.toUri
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import com.atharok.btremote.BuildConfig

enum class UpdateStage { IDLE,CHECKING,CURRENT,AVAILABLE,DOWNLOADING,VERIFYING,READY,ERROR }
data class UpdateState(val stage:UpdateStage=UpdateStage.IDLE,val release:UpdateRelease?=null,
    val downloaded:Long=0,val message:String="")
/** Downloads belong to this app only. Android retains the transfer if the process is stopped. */
class AppUpdater private constructor(private val context:Context) {
    private val downloads=context.getSystemService(DownloadManager::class.java)
    private val prefs=context.getSharedPreferences("app_update_private",Context.MODE_PRIVATE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val state=MutableStateFlow(UpdateState())
    private var job:Job?=null
    @Suppress("DEPRECATION")
    private fun installed()=identity(context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES))
    private fun identity(p:PackageInfo)=ApkIdentity(p.packageName,p.versionName?:"",p.longVersionCode,
        p.signingInfo?.apkContentsSigners?.map { UpdateIntegrity.sha256(it.toByteArray()) }?.toSet()?:emptySet())
    val currentVersion get()=installed().versionName
    private fun file(r:UpdateRelease)=File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?:error("安装包存储不可用"),"updates/"+r.name)
    init {
        val id=prefs.getLong("download",-1)
        val saved=prefs.getString("release",null)
        if(id>=0 && saved!=null)job=scope.launch {
            try {
                val r=UpdateCatalog.release(Json.parseToJsonElement(saved).jsonObject)
                if(UpdateCatalog.compare(r.version,currentVersion)<=0) {
                    downloads.remove(id);prefs.edit { clear() }
                    state.value=UpdateState(UpdateStage.CURRENT,message="已完成更新，当前版本 "+currentVersion)
                }
                else monitor(id,r)
            } catch(e:Exception) { fail("无法恢复下载，请重新检查更新") }
        }
    }
    @Synchronized fun check() {
        if(job?.isActive==true || state.value.stage==UpdateStage.READY)return
        if(BuildConfig.UPDATE_ORIGIN.isBlank()) {
            state.value=UpdateState(UpdateStage.CURRENT,message="此构建未配置应用更新站点，请从部署者提供的地址获取安装包")
            return
        }
        job=scope.launch {
            state.value=UpdateState(UpdateStage.CHECKING,message="正在检查更新…")
            try {
                val c=URL(UpdateCatalog.endpoint()).openConnection() as HttpURLConnection
                val raw=try {
                    c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=20000
                    c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","AiPhoneKVM/$currentVersion")
                    check(c.responseCode==200) { "服务器暂不可用" }
                    val out=ByteArrayOutputStream()
                    c.inputStream.use { input ->
                        val buffer=ByteArray(8192)
                        while(true) { val n=input.read(buffer);if(n<0)break;check(out.size()+n<=1024*1024);out.write(buffer,0,n) }
                    }
                    out.toString(Charsets.UTF_8.name())
                } finally { c.disconnect() }
                val r=UpdateCatalog.latest(raw)
                state.value=if(r!=null && UpdateCatalog.compare(r.version,currentVersion)>0)
                    UpdateState(UpdateStage.AVAILABLE,r,message="发现新版本 "+r.version)
                else UpdateState(UpdateStage.CURRENT,message="当前已是最新版本")
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { fail("检查更新失败，请检查网络后重试") }
        }
    }
    @Synchronized fun download() {
        if(job?.isActive==true)return
        val r=state.value.release?:return
        job=scope.launch {
            try {
                check(UpdateCatalog.compare(r.version,currentVersion)>0)
                val target=file(r);target.parentFile!!.mkdirs()
                if(target.exists())check(target.delete()) { "无法清理旧安装包" }
                val request=DownloadManager.Request(r.url.toUri()).setTitle("AiPhoneKVM "+r.version)
                    .setDescription("下载应用更新").setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(target))
                val id=downloads.enqueue(request)
                if(!rememberDownload(id,r)) {
                    downloads.remove(id);error("保存下载任务失败")
                }
                monitor(id,r)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { fail(e.message?:"下载失败，请重试",r) }
        }
    }
    // The download must not be orphaned if its durable record cannot be committed.
    @SuppressLint("UseKtx")
    private fun rememberDownload(id:Long,r:UpdateRelease)=prefs.edit().putLong("download",id).putString("release",r.json().toString()).commit()
    private suspend fun monitor(id:Long,r:UpdateRelease) {
        try {
            while(true) {
                currentCoroutineContext().ensureActive()
                val row=downloads.query(DownloadManager.Query().setFilterById(id)).use { c->
                    check(c.moveToFirst()) { "下载任务已移除" }
                    Triple(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                }
                when(row.first) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        state.value=UpdateState(UpdateStage.VERIFYING,r,message="正在校验安装包…")
                        verify(r)
                        currentCoroutineContext().ensureActive()
                        state.value=UpdateState(UpdateStage.READY,r,r.size,"校验通过，可以安装更新")
                        return
                    }
                    DownloadManager.STATUS_FAILED -> error("下载失败，请重试（系统代码 "+row.third+"）")
                    else -> state.value=UpdateState(UpdateStage.DOWNLOADING,r,row.second,
                        if(row.first==DownloadManager.STATUS_PAUSED)"等待网络恢复…" else "正在下载安装包…")
                }
                check(row.second<=r.size) { "安装包大小与发布信息不一致" }
                delay(700)
            }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            currentCoroutineContext().ensureActive()
            downloads.remove(id);prefs.edit { clear() }
            fail(e.message?:"安装包校验失败",r)
        }
    }
    @Suppress("DEPRECATION")
    private fun verify(r:UpdateRelease) {
        val f=file(r);check(f.isFile && f.length()==r.size) { "安装包大小不一致，请重新下载" }
        val h=MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input->val b=ByteArray(65536);while(true) { val n=input.read(b);if(n<0)break;h.update(b,0,n) } }
        check(h.digest().joinToString("") { "%02x".format(it) }==r.sha256) { "安装包SHA-256不一致，已拒绝安装" }
        val p=context.packageManager.getPackageArchiveInfo(f.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)?:error("安装包无法识别")
        UpdateIntegrity.identity(identity(p),installed(),r)
    }
    @Synchronized fun cancelDownload() {
        val id=prefs.getLong("download",-1);job?.cancel()
        if(id>=0)downloads.remove(id)
        prefs.edit { clear() }
        state.value=state.value.copy(stage=UpdateStage.AVAILABLE,downloaded=0,message="下载已取消")
    }
    @Synchronized fun install(activity:Activity) {
        if(job?.isActive==true || state.value.stage!=UpdateStage.READY)return
        val r=state.value.release?:return
        job=scope.launch {
            try {
                verify(r)
                withContext(Dispatchers.Main) {
                    if(!context.packageManager.canRequestPackageInstalls()) {
                        state.value=state.value.copy(message="请允许本应用安装更新，返回后再次点击安装。")
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,("package:"+context.packageName).toUri()))
                    } else {
                        val uri=downloads.getUriForDownloadedFile(prefs.getLong("download",-1))?:error("安装包已丢失，请重新下载")
                        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { fail(e.message?:"无法打开系统安装程序",r) }
        }
    }
    private fun fail(message:String,r:UpdateRelease?=null) { state.value=UpdateState(UpdateStage.ERROR,r,message=message.take(180)) }
    companion object {
        @Volatile private var instance:AppUpdater?=null
        fun get(context:Context)=instance?:synchronized(this) { instance?:AppUpdater(context.applicationContext).also { instance=it } }
    }
}
