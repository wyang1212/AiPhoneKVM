// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import android.os.SystemClock
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Network worker belongs to the explicitly authorized screen-share session, not the IME. */
class CompanionControlClient(private val context: android.content.Context,private val address: String,private val token: String) {
    private val transport=CaptureTransport()
    private val worker=Executors.newSingleThreadExecutor()
    private val session=UUID.randomUUID().toString()
    @Volatile private var alive=true
    fun start() { worker.submit { poll() } }
    fun stop() { alive=false;transport.close();worker.shutdownNow() }
    private fun request(path: String, body: JSONObject) = transport.request(address,token,path,body.toString())
    private fun poll() {
        while(alive) {
            try {
                val service=EnhancedControlService.instance
                val response=request("/api/companion/poll",JSONObject().put("session",session).put("enabled",service!=null).put("display",DisplaySnapshot.read(context)))
                if(response.first==204) continue
                if(response.first!=200) { Thread.sleep(1500);continue }
                val command=JSONObject(response.second);val id=command.getString("id")
                val sentAt=SystemClock.elapsedRealtime()
                val lease=request("/api/companion/start",JSONObject().put("id",id).put("session",session))
                if(lease.first!=200) continue
                val result=CompletableFuture<JSONObject>()
                val current=EnhancedControlService.instance
                if(current==null) result.complete(EnhancedControlService.failed("SERVICE_DISABLED"))
                else current.execute(command,sentAt,{ alive }) { result.complete(it) }
                val answer=try { result.get(3500,TimeUnit.MILLISECONDS) }
                    catch(_: java.util.concurrent.TimeoutException) { EnhancedControlService.failed("EXECUTION_TIMEOUT") }
                if(alive) request("/api/companion/result",answer.put("id",id).put("session",session))
            } catch(_: InterruptedException) { break }
            catch(_: Exception) { if(alive) try { Thread.sleep(1500) } catch(_:InterruptedException) { break } }
        }
    }
}
