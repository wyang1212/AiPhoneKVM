// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Small fixed-length HTTP exchanges; no pooled idle socket survives a LAN interruption. */
class CaptureTransport {
    private val client=OkHttpClient.Builder()
        .connectTimeout(8,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .writeTimeout(15,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS))
        .retryOnConnectionFailure(true).followRedirects(false).followSslRedirects(false).build()
    private val lock=Any()
    private var closed=false
    @Volatile private var active: Call?=null

    fun request(address: String,token: String,path: String,body: String?=null): Pair<Int,String> {
        val request=Request.Builder().url(address+path)
            .header("Authorization","Bearer $token").header("Accept-Encoding","identity")
            .header("Connection","close").header("User-Agent","AiPhoneKVM-Capture/${BuildConfig.VERSION_NAME}")
            .apply { if(body!=null) post(body.toRequestBody("application/json; charset=utf-8".toMediaType())) }.build()
        val call=synchronized(lock) {
            check(!closed) { "截图连接已停止" }
            client.newCall(request).also { active=it }
        }
        try {
            return call.execute().use { response ->
                val text=response.body.byteStream().use { input ->
                    val out=ByteArrayOutputStream();val bytes=ByteArray(2048)
                    while(true) {
                        val n=input.read(bytes);if(n<0)break
                        require(out.size()+n<=20000) { "主控端响应超过20KiB" }
                        out.write(bytes,0,n)
                    }
                    out.toString("UTF-8")
                }
                response.code to text
            }
        } finally { synchronized(lock) { if(active===call) active=null } }
    }
    fun close() {
        synchronized(lock) { closed=true;active?.cancel() }
        client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()
    }
}
