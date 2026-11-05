package com.atharok.btremote.automation

import kotlinx.serialization.json.*
import java.net.URI
import java.util.UUID

/** Target dimensions describe the controlled device, never the intermediary phone. */
data class AutomationConfig(
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val sensitivity: Double = 1.0,
    val moveDelayMs: Int = 16,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val mode: String = "combined",
    val httpPort: Int = 8765,
    val mqttUri: String = "",
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val caCert: String = "",
    val topicPrefix: String = "bluehidflow/",
    val deviceId: String = UUID.randomUUID().toString().replace("-", "").take(12).uppercase(),
    val captureToken: String = UUID.randomUUID().toString().replace("-", ""),
    val token: String = UUID.randomUUID().toString().replace("-", "")
) {
    fun validate(): AutomationConfig {
        require(screenWidth in 100..10000 && screenHeight in 100..10000) { "屏幕宽高必须为100–10000" }
        require(sensitivity.isFinite() && sensitivity in 0.1..20.0) { "灵敏度必须为0.1–20" }
        require(moveDelayMs in 8..100) { "移动间隔必须为8–100ms" }
        require(mode in listOf("combined", "mouse", "keyboard")) { "模式无效" }
        require(httpPort in 1024..65535) { "端口必须为1024–65535" }
        require(topicPrefix.length in 1..100 && topicPrefix.none { it in "+#\u0000" }) { "MQTT主题前缀无效" }
        require(deviceId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "设备ID无效" }
        require(token.length >= 24) { "访问令牌太短" }
        if (mqttUri.isNotBlank()) {
            val uri = URI(mqttUri)
            require(uri.scheme in listOf("tcp", "ssl") && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.port in 1..65535 && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
                "MQTT地址格式：ssl://主机:8883 或 tcp://主机:1883"
            }
        }
        require(mqttUsername.length <= 256 && mqttPassword.length <= 1024 && caCert.length <= 12000)
        return this
    }

    fun json(secrets: Boolean = false): JsonObject = buildJsonObject {
        putJsonObject("phone") {
            put("screenWidth", screenWidth); put("screenHeight", screenHeight)
            put("hidSensitivity", sensitivity); put("moveDelayMs", moveDelayMs)
            put("invertX", invertX); put("invertY", invertY)
        }
        putJsonObject("bluetooth") { put("hidMode", mode) }
        putJsonObject("mqtt") {
            put("uri", mqttUri); put("username", mqttUsername); put("topicPrefix", topicPrefix)
            put("deviceId", deviceId); put("hasPassword", mqttPassword.isNotEmpty()); put("hasCaCert", caCert.isNotEmpty())
            if (secrets) { put("password", mqttPassword); put("caCert", caCert) }
        }
        put("httpPort", httpPort)
        if (secrets) { put("token", token); put("captureToken", captureToken) }
    }

    fun merge(input: JsonObject, network: Boolean = false): AutomationConfig {
        val allowed = if (network) setOf("phone", "bluetooth", "mqtt", "httpPort", "token", "captureToken") else setOf("phone", "bluetooth")
        require(input.keys.all { it in allowed }) { "不支持的配置节；网络设置请使用配置页" }
        val phone = input["phone"]?.jsonObject ?: JsonObject(emptyMap())
        val bt = input["bluetooth"]?.jsonObject ?: JsonObject(emptyMap())
        val mqtt = input["mqtt"]?.jsonObject ?: JsonObject(emptyMap())
        require(phone.keys.all { it in setOf("screenWidth", "screenHeight", "hidSensitivity", "moveDelayMs", "invertX", "invertY", "model") }) { "未知手机配置字段" }
        require(bt.keys.all { it == "hidMode" }) { "蓝牙名称请在系统设置修改" }
        require(mqtt.keys.all { it in setOf("uri", "username", "password", "caCert", "topicPrefix", "deviceId", "hasPassword", "hasCaCert") }) { "未知MQTT字段" }
        return copy(
            screenWidth = phone.intValue("screenWidth", screenWidth), screenHeight = phone.intValue("screenHeight", screenHeight),
            sensitivity = phone.doubleValue("hidSensitivity", sensitivity), moveDelayMs = phone.intValue("moveDelayMs", moveDelayMs),
            invertX = phone.boolValue("invertX", invertX), invertY = phone.boolValue("invertY", invertY),
            mode = bt.stringValue("hidMode", mode).lowercase(), httpPort = input.intValue("httpPort", httpPort),
            mqttUri = mqtt.stringValue("uri", mqttUri).trim(), mqttUsername = mqtt.stringValue("username", mqttUsername),
            mqttPassword = mqtt.stringValue("password", mqttPassword), caCert = mqtt.stringValue("caCert", caCert),
            topicPrefix = mqtt.stringValue("topicPrefix", topicPrefix).let { if (it.endsWith('/')) it else "$it/" },
            deviceId = mqtt.stringValue("deviceId", deviceId), captureToken = input.stringValue("captureToken", captureToken), token = input.stringValue("token", token)
        ).validate()
    }
}

internal fun JsonObject.intValue(key: String, default: Int? = null): Int =
    if (!containsKey(key) && default != null) default else this[key]?.jsonPrimitive?.intOrNull
        ?: throw IllegalArgumentException("$key 必须为整数")
internal fun JsonObject.doubleValue(key: String, default: Double): Double =
    if (!containsKey(key)) default else this[key]?.jsonPrimitive?.doubleOrNull
        ?: throw IllegalArgumentException("$key 必须为数字")
internal fun JsonObject.stringValue(key: String, default: String? = null): String =
    if (!containsKey(key) && default != null) default else this[key]?.jsonPrimitive?.takeIf { it.isString }?.content
        ?: throw IllegalArgumentException("$key 必须为字符串")
internal fun JsonObject.boolValue(key: String, default: Boolean): Boolean =
    if (!containsKey(key)) default else this[key]?.jsonPrimitive?.booleanOrNull
        ?: throw IllegalArgumentException("$key 必须为布尔值")
