// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.*

class CaptureTransportTest {
    private fun receive(socket: Socket): Pair<List<String>,ByteArray> {
        val input=socket.getInputStream().buffered()
        fun line(): String {
            val out=java.io.ByteArrayOutputStream()
            while(true) { val b=input.read();if(b<0||b==10)break;if(b!=13)out.write(b) }
            return out.toString("UTF-8")
        }
        val headers=mutableListOf<String>()
        while(true) { val l=line();if(l.isEmpty())break;headers+=l }
        val n=headers.firstOrNull { it.startsWith("Content-Length:",true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
        val data=ByteArray(n);var offset=0
        while(offset<n) { val count=input.read(data,offset,n-offset);require(count>0);offset+=count }
        return headers to data
    }
    private fun respond(socket: Socket,code: Int,body: String="") {
        val bytes=body.toByteArray();socket.getOutputStream().write("HTTP/1.1 $code ${if(code==204) "No Content" else "OK"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()+bytes)
    }
    @Test fun emptyPollCanBeFollowedByCommandOnNewConnection() {
        ServerSocket(0).use { server ->
            val future=CompletableFuture.supplyAsync {
                server.accept().use { receive(it);respond(it,204) }
                server.accept().use { receive(it);respond(it,200,"{\"id\":\"request\"}") }
            }
            val transport=CaptureTransport()
            try {
                val url="http://127.0.0.1:${server.localPort}"
                assertEquals(204 to "",transport.request(url,"token","/next"))
                assertEquals(200 to "{\"id\":\"request\"}",transport.request(url,"token","/next"))
                future.get(5,TimeUnit.SECONDS)
            } finally { transport.close() }
        }
    }
    @Test fun frameUploadUsesExactUtf8ContentLengthAndAuth() {
        ServerSocket(0).use { server ->
            val sent="{\"image\":\""+"x".repeat(100000)+"\",\"label\":\"截图\"}"
            val future=CompletableFuture.supplyAsync {
                server.accept().use { socket ->
                    val (headers,body)=receive(socket)
                    assertTrue(headers.any { it=="Authorization: Bearer paired-token" })
                    assertTrue(headers.any { it=="Connection: close" });assertArrayEquals(sent.toByteArray(),body)
                    respond(socket,200,"{\"accepted\":true}")
                }
            }
            val transport=CaptureTransport()
            try { assertEquals(200,transport.request("http://127.0.0.1:${server.localPort}","paired-token","/frame",sent).first);future.get(5,TimeUnit.SECONDS) }
            finally { transport.close() }
        }
    }
    @Test fun oversizedResponseIsBounded() {
        ServerSocket(0).use { server ->
            val future=CompletableFuture.runAsync { server.accept().use { receive(it);respond(it,200,"x".repeat(21000)) } }
            val transport=CaptureTransport()
            try { assertThrows(IllegalArgumentException::class.java) { transport.request("http://127.0.0.1:${server.localPort}","token","/next") };future.get(5,TimeUnit.SECONDS) }
            finally { transport.close() }
        }
    }
    @Test fun stopCancelsAnOutstandingPoll() {
        ServerSocket(0).use { server ->
            val connected=CountDownLatch(1)
            val peer=CompletableFuture.runAsync { server.accept().use { receive(it);connected.countDown();Thread.sleep(1200) } }
            val transport=CaptureTransport()
            val poll=CompletableFuture.supplyAsync { runCatching { transport.request("http://127.0.0.1:${server.localPort}","token","/next") } }
            assertTrue(connected.await(3,TimeUnit.SECONDS));transport.close()
            assertTrue(poll.get(3,TimeUnit.SECONDS).isFailure);peer.get(3,TimeUnit.SECONDS)
        }
    }
    @Test fun stoppedTransportCannotStartALateFrameUpload() {
        val transport=CaptureTransport();transport.close()
        assertThrows(IllegalStateException::class.java) {
            transport.request("http://127.0.0.1:8765","token","/frame","{}")
        }
    }

}
