// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.content.Context
import android.annotation.SuppressLint
import android.os.Build
import com.atharok.btremote.BuildConfig
import com.atharok.btremote.common.utils.httpsOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import java.util.Base64

// Only for migrating bindings/journals created before configurable websites (2.7).
internal const val WEBSITE_ORIGIN = "https://controller.example.invalid"

internal fun pairingToken(link: String, origin: String = WEBSITE_ORIGIN): String {
    val uri = URI(link)
    require(httpsOrigin(URI(uri.scheme, uri.rawAuthority, null, null, null).toString()) == httpsOrigin(origin) &&
        uri.rawPath == "/pair" && uri.userInfo == null && uri.rawQuery == null) { "请扫描设备控制台生成的绑定二维码" }
    val fields = uri.rawFragment?.split('&')?.map { it.split('=', limit = 2) }
        ?: error("二维码缺少绑定信息")
    require(fields.size == 2 && fields.all { it.size == 2 } && fields.map { it[0] }.toSet() == setOf("v", "token")) { "二维码格式无效" }
    val map = fields.associate { it[0] to it[1] }
    require(map["v"] == "1" && map["token"]!!.matches(Regex("[A-Za-z0-9_-]{43}"))) { "二维码版本或令牌无效，请刷新二维码" }
    return map["token"]!!
}

internal fun enrollmentConfig(old: AutomationConfig, connection: JsonObject): AutomationConfig {
    val broker = connection.stringValue("uri")
    require(URI(broker).scheme == "ssl" && connection.stringValue("device_id") == old.deviceId &&
        connection.stringValue("client_id") == "aiphone-${old.deviceId}") { "绑定服务器配置无效" }
    val prefix = connection.stringValue("topic_prefix")
    require(prefix.matches(Regex("aiphonekvm/[a-f0-9]{32}/[a-f0-9]{32}/"))) { "绑定主题配置无效" }
    val username = connection.stringValue("username")
    val password = connection.stringValue("password")
    require(username.matches(Regex("phone-[a-f0-9]{32}")) && password.matches(Regex("[A-Za-z0-9_-]{43}"))) { "绑定凭据无效" }
    return old.copy(mqttUri = broker, mqttUsername = username, mqttPassword = password,
        caCert = "", topicPrefix = prefix).validate()
}

/** Apply only MQTT fields, including during rollback; keep any newer local settings. */
internal fun storedMqttProfile(current: AutomationConfig, saved: JsonObject): AutomationConfig {
    val mqtt = saved["mqtt"]!!.jsonObject
    require(mqtt.stringValue("deviceId") == current.deviceId) { "设备标识已变化，请核对原网站绑定" }
    return current.copy(mqttUri = mqtt.stringValue("uri"), mqttUsername = mqtt.stringValue("username"),
        mqttPassword = mqtt.stringValue("password"), topicPrefix = mqtt.stringValue("topicPrefix"),
        caCert = mqtt.stringValue("caCert")).validate()
}

internal fun enrollmentMessage(error: Exception): String = when (error) {
    is javax.net.ssl.SSLException -> "加密连接验证失败，请检查手机时间与网络后重试"
    is java.io.IOException -> "无法连接网站，请检查网络后重试"
    else -> error.message?.take(180) ?: "绑定未完成，请重试"
}

internal class EnrollmentApi(origin: String) {
    private val origin = httpsOrigin(origin)
    fun post(path: String, body: JsonObject): JsonObject {
        require(path in setOf("inspect", "prepare", "finish", "recover"))
        val conn = URL("$origin/api/enrollment/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000; conn.readTimeout = 20000; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Origin", origin)
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val result = stream?.use {
                val buffer = ByteArray(32769); var count = 0
                while (count < buffer.size) {
                    val read = it.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count)
            } ?: byteArrayOf()
            require(result.size <= 32768) { "服务器回复过大" }
            val data = runCatching { Json.parseToJsonElement(String(result, Charsets.UTF_8)).jsonObject }
                .getOrElse { error("网站回复格式无效，请稍后重试") }
            if (code !in 200..299) error((data["detail"] as? JsonPrimitive)?.content?.take(160) ?: "绑定请求未完成")
            return data
        } finally { conn.disconnect() }
    }
}

/** No command subscription: this short-lived TLS connection only proves the phone's own role. */
internal fun publishEnrollmentProof(config: AutomationConfig, challenge: String) {
    require(URI(config.mqttUri).scheme == "ssl" && challenge.matches(Regex("[A-Za-z0-9_-]{43}"))) { "原连接无法自动验证，请在原账号解除设备后再扫码" }
    config.validate()
    val client = MqttClient(config.mqttUri, "aiphone-${config.deviceId}", MemoryPersistence())
    try {
        client.timeToWait = 15000
        client.connect(MqttConnectOptions().apply {
            isCleanSession = true; isAutomaticReconnect = false; connectionTimeout = 12; keepAliveInterval = 20
            userName = config.mqttUsername; password = config.mqttPassword.toCharArray()
            isHttpsHostnameVerificationEnabled = true
        })
        val proof = buildJsonObject {
            put("pairing_proof", challenge); put("device_id", config.deviceId)
            put("running", false); put("status", "pairing"); put("timestamp", System.currentTimeMillis())
        }
        client.publish("${config.topicPrefix}status/${config.deviceId}", proof.toString().toByteArray(), 1, false)
    } finally {
        runCatching { if (client.isConnected) client.disconnect() }
        runCatching { client.close() }
    }
}

// commit() is checked so a failed local save can never be reported as a successful binding.
@SuppressLint("ApplySharedPref", "UseKtx")
internal class WebsiteEnrollment(context: Context) {
    companion object { private val transaction = Mutex() }
    private val prefs = context.getSharedPreferences("website_binding_private", Context.MODE_PRIVATE)
    private val runtime = AutomationRuntime.get(context)
    val origin: String get() = httpsOrigin(prefs.getString("origin", null) ?: BuildConfig.WEBSITE_ORIGIN)
    private val boundOrigin: String get() = prefs.getString("bound_origin", null) ?: WEBSITE_ORIGIN
    val account: String get() = if (boundOrigin == origin) prefs.getString("account", "") ?: "" else ""
    val pending: Boolean get() = prefs.contains("pending")
    suspend fun changeOrigin(value: String) = withContext(Dispatchers.IO) { transaction.withLock {
        check(!pending) { "请先恢复上次配对，再修改网站地址" }
        val next = httpsOrigin(value)
        if (next != origin) {
            runtime.stopAndAwait()
            save("origin", next)
        }
    } }
    private fun save(key: String, value: String?) {
        check(prefs.edit().putString(key, value).commit()) { "保存绑定信息失败" }
    }
    suspend fun inspect(token: String, site: String = origin) = withContext(Dispatchers.IO) {
        EnrollmentApi(site).post("inspect", buildJsonObject { put("token", token) }).stringValue("account")
    }
    suspend fun bind(token: String, account: String, site: String, progress: (String) -> Unit) = withContext(NonCancellable + Dispatchers.IO) { transaction.withLock {
        check(!pending) { "请先恢复上次未完成的配对" }
        check(site == origin) { "网站地址已变化，请重新扫码" }
        val api = EnrollmentApi(site)
        progress("正在停止控制并释放按键…")
        runtime.stopAndAwait()
        val old = runtime.store.state.value
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val body = buildJsonObject {
            put("token", token); put("nonce", nonce); put("device_id", old.deviceId)
            put("name", Build.MODEL.take(40)); put("binding_secret", if (site == boundOrigin) prefs.getString("binding_secret", "") ?: "" else "")
        }
        // Journal even before prepare: network loss after provisioning must remain recoverable.
        save("pending", buildJsonObject { put("origin", site); put("body", body); put("old", old.json(true)); put("account", account) }.toString())
        try {
            progress("正在验证设备归属…")
            var prepared = api.post("prepare", body)
            if (prepared["proof_required"]?.jsonPrimitive?.booleanOrNull == true) {
                check(site == boundOrigin) { "请先在此网站解除原设备，再重新扫码" }
                publishEnrollmentProof(old, prepared.stringValue("challenge"))
                repeat(5) {
                    if (prepared["proof_required"]?.jsonPrimitive?.booleanOrNull == true) {
                        Thread.sleep(200)
                        prepared = api.post("prepare", body)
                    }
                }
                check(prepared["proof_required"]?.jsonPrimitive?.booleanOrNull != true) { "原连接归属验证尚未到达，请重试" }
            }
            val next = enrollmentConfig(old, prepared["connection"]!!.jsonObject)
            val journal = buildJsonObject {
                put("origin", site)
                put("body", body); put("old", old.json(true)); put("next", next.json(true))
                put("account", account); put("binding_secret", prepared.stringValue("binding_secret"))
            }
            save("pending", journal.toString())
            complete(journal, progress)
        } catch (e: Exception) {
            resolveFailure(e)
        }
    } }
    private fun complete(journal: JsonObject, progress: (String) -> Unit) {
        val api = EnrollmentApi(journalOrigin(journal))
        val body = journal["body"]!!.jsonObject
        val next = storedMqttProfile(runtime.store.state.value, journal["next"]!!.jsonObject)
        progress("正在保存配置并验证加密连接…")
        runtime.store.save(next)
        val prepared = api.post("prepare", body)
        publishEnrollmentProof(next, prepared.stringValue("challenge"))
        progress("等待网站确认…")
        // Broker delivery can race HTTP by a few milliseconds; retry this idempotent step only.
        var result: JsonObject? = null
        repeat(4) {
            if (result == null) {
                try { result = api.post("finish", body) }
                catch (e: Exception) { if (it == 3) throw e; Thread.sleep(400) }
            }
        }
        finalizeLocal(journal)
    }
    private fun finalizeLocal(journal: JsonObject) {
        check(prefs.edit().putString("binding_secret", journal.stringValue("binding_secret"))
            .putString("bound_origin", journalOrigin(journal))
            .putString("account", journal.stringValue("account")).remove("pending").commit()) { "保存绑定确认失败，请恢复上次配对" }
    }
    private fun resolveFailure(original: Exception) {
        val journal = Json.parseToJsonElement(prefs.getString("pending", null) ?: throw original).jsonObject
        val resolved = runCatching { EnrollmentApi(journalOrigin(journal)).post("recover", journal["body"]!!.jsonObject).stringValue("state") }.getOrNull()
        when (resolved) {
            "complete" -> { finalizeLocal(journal); return }
            "cancelled" -> {
                runtime.store.save(storedMqttProfile(runtime.store.state.value, journal["old"]!!.jsonObject))
                save("pending", null)
                throw IllegalStateException("${enrollmentMessage(original)}。已恢复原连接设置，请刷新二维码重试")
            }
            else -> throw IllegalStateException("网络中断，绑定结果尚未确认。请保持此页，联网后点击恢复上次配对；原配置已安全保留")
        }
    }
    suspend fun recover(progress: (String) -> Unit) = withContext(NonCancellable + Dispatchers.IO) { transaction.withLock {
        runtime.stopAndAwait()
        val journal = Json.parseToJsonElement(prefs.getString("pending", null) ?: error("没有待恢复配对")).jsonObject
        val api = EnrollmentApi(journalOrigin(journal))
        progress("正在核对上次绑定结果…")
        try {
            val result = api.post("recover", journal["body"]!!.jsonObject).stringValue("state")
            if (result == "complete") {
                runtime.store.save(storedMqttProfile(runtime.store.state.value, journal["next"]!!.jsonObject))
                finalizeLocal(journal)
            } else {
                runtime.store.save(storedMqttProfile(runtime.store.state.value, journal["old"]!!.jsonObject))
                save("pending", null)
                error("上次配对已取消，原配置已恢复，请重新扫码")
            }
        } catch (e: Exception) { throw IllegalStateException(e.message?.take(180) ?: "请联网后重试") }
    } }
}

/** Older journals were created exclusively by the original public website. */
internal fun journalOrigin(journal: JsonObject): String = httpsOrigin(journal.stringValue("origin", WEBSITE_ORIGIN))
