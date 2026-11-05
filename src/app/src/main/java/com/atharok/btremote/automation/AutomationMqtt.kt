// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class AutomationMqtt(private val runtime: AutomationRuntime, private val config: AutomationConfig, private val scope: CoroutineScope) {
    private val client = MqttAsyncClient(config.mqttUri,"aiphone-${config.deviceId}",MemoryPersistence())
    @Volatile private var stopped = false
    private var heartbeat: Job? = null
    private val prefix = config.topicPrefix
    private fun topic(kind: String) = "$prefix$kind/${config.deviceId}"
    fun start() {
        runtime.mqttState.value = "连接中"
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if(stopped) return
                client.subscribe(topic("command"),1,null,object : IMqttActionListener {
                    override fun onSuccess(token: IMqttToken?) {
                        runtime.mqttState.value = "已连接"; publishStatus()
                    }
                    override fun onFailure(token: IMqttToken?, error: Throwable?) { runtime.mqttState.value = "订阅失败" }
                })
            }
            override fun connectionLost(cause: Throwable?) { if(!stopped) runtime.mqttState.value = "连接断开，重连中" }
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if(stopped || message == null || topic != topic("command")) return
                if(message.isRetained) { runtime.record("拒绝 retained 控制命令"); return }
                if(message.payload.size > 16384) { publish("response",CommandEngine.response("","FAILED","TOO_LARGE","命令超过16KiB").toString()); return }
                val raw = String(message.payload,Charsets.UTF_8)
                scope.launch { val response = runtime.submit(raw); publishStatus(); publish("response",response.toString()) }
            }
        })
        scope.launch {
            while(isActive && !stopped) {
                try {
                    val options = MqttConnectOptions().apply {
                        isCleanSession = true; isAutomaticReconnect = true
                        connectionTimeout = 10; keepAliveInterval = 30
                        if(config.mqttUsername.isNotEmpty()) userName = config.mqttUsername
                        if(config.mqttPassword.isNotEmpty()) password = config.mqttPassword.toCharArray()
                        setWill(topic("status"),buildJsonObject { put("device_id",config.deviceId);put("status","offline") }.toString().toByteArray(),1,true)
                        if(config.mqttUri.startsWith("ssl://")) {
                            isHttpsHostnameVerificationEnabled = true
                            if(config.caCert.isNotBlank()) {
                                val certs = CertificateFactory.getInstance("X.509").generateCertificates(config.caCert.byteInputStream())
                                require(certs.isNotEmpty())
                                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null,null) }
                                certs.forEachIndexed { i, cert -> ks.setCertificateEntry("ca-$i",cert) }
                                val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
                                socketFactory = SSLContext.getInstance("TLS").apply { init(null,tm.trustManagers,null) }.socketFactory
                            }
                        }
                    }
                    client.connect(options).waitForCompletion(12000)
                    break
                } catch(_: Exception) { if(!stopped) { runtime.mqttState.value = "连接失败，稍后重试（检查地址/凭据/证书）"; delay(5000) } }
            }
        }
        heartbeat = scope.launch { while(isActive && !stopped) { delay(if (runtime.goalLoop.active) 3000 else 10000);publishStatus() } }
    }
    private fun publishStatus() = publish("status",runtime.status().toString(),true)
    fun log(message: String) = publish("log",buildJsonObject { put("message",message);put("timestamp",System.currentTimeMillis()) }.toString())
    private fun publish(kind: String, payload: String, retain: Boolean = false) {
        if(stopped || !client.isConnected) return
        runCatching { client.publish(topic(kind),payload.toByteArray(),1,retain) }
    }
    fun stop() {
        if(stopped) return
        publish("status",buildJsonObject { put("device_id",config.deviceId);put("status","offline") }.toString(),true)
        stopped = true; heartbeat?.cancel()
        runCatching { client.disconnectForcibly(0,1000) }; runCatching { client.close(true) }
    }
}
