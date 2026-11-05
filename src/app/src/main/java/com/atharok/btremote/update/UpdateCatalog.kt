// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.update

import kotlinx.serialization.json.*
import java.security.MessageDigest
import com.atharok.btremote.BuildConfig
import com.atharok.btremote.common.utils.httpsOrigin

data class UpdateRelease(val id:String,val name:String,val version:String,val title:String,val notes:String,val size:Long,val sha256:String) {
    val url get()=UpdateCatalog.downloadUrl(id)
    fun json()=buildJsonObject {
        put("id",id);put("name",name);put("title",title);put("description",notes);put("size",size);put("sha256",sha256)
    }
}
object UpdateCatalog {
    fun endpoint(origin: String = BuildConfig.UPDATE_ORIGIN) = "${httpsOrigin(origin)}/api/files"
    fun downloadUrl(id: String, origin: String = BuildConfig.UPDATE_ORIGIN): String {
        require(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(id))
        return "${httpsOrigin(origin)}/download/$id"
    }
    const val MAX_APK_BYTES=512L*1024*1024
    private val filename=Regex("AiPhoneKVM-([0-9]+(?:\\.[0-9]+){0,5})-(?:android|debug)\\.apk")
    fun compare(a:String,b:String):Int {
        fun parts(s:String)=s.split('.').also { require(it.size<=6) }.map { it.toInt().also { n->require(n>=0) } }
        val x=parts(a);val y=parts(b)
        for(i in 0 until maxOf(x.size,y.size)) { val c=(x.getOrElse(i){0}).compareTo(y.getOrElse(i){0});if(c!=0)return c }
        return 0
    }
    fun release(o:JsonObject):UpdateRelease {
        fun str(k:String)=o[k]?.jsonPrimitive?.content?:error("更新目录缺少字段")
        val name=str("name");val version=filename.matchEntire(name)?.groupValues?.get(1)?:error("不是主控安装包")
        compare(version,version)
        val id=str("id");require(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(id))
        val size=o["size"]?.jsonPrimitive?.long?:error("缺少安装包大小");require(size in 1..MAX_APK_BYTES)
        val hash=str("sha256").lowercase();require(Regex("[0-9a-f]{64}").matches(hash))
        return UpdateRelease(id,name,version,o["title"]?.jsonPrimitive?.content?:name,
            (o["description"]?.jsonPrimitive?.content?:"").take(12000),size,hash)
    }
    fun latest(raw:String):UpdateRelease? {
        require(raw.length<=1024*1024)
        val root=Json.parseToJsonElement(raw)
        val files=if(root is JsonArray)root else root.jsonObject.getValue("files").jsonArray
        val candidates=files.map { it.jsonObject }.filter { filename.matches(it["name"]?.jsonPrimitive?.content?:"") }
        val selected=candidates.maxWithOrNull { a,b->
            compare(filename.matchEntire(a.getValue("name").jsonPrimitive.content)!!.groupValues[1],
                filename.matchEntire(b.getValue("name").jsonPrimitive.content)!!.groupValues[1])
        }
        return selected?.let(::release)
    }
}
data class ApkIdentity(val packageName:String,val versionName:String,val versionCode:Long,val signerHashes:Set<String>)
object UpdateIntegrity {
    fun identity(candidate:ApkIdentity,installed:ApkIdentity,release:UpdateRelease) {
        require(candidate.packageName==installed.packageName) { "安装包不是当前应用" }
        require(candidate.versionName==release.version && candidate.versionCode>installed.versionCode) { "安装包版本无效或不是更新版本" }
        require(candidate.signerHashes.isNotEmpty() && candidate.signerHashes==installed.signerHashes) { "安装包签名与当前应用不同" }
    }
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
