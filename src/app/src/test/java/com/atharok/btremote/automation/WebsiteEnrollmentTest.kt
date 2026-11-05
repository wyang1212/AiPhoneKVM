// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WebsiteEnrollmentTest {
    private val token = "example_" + "a".repeat(35)
    private val broker = "ssl://mqtt.example.invalid:8883"
    @Test fun onlyOfficialVersionedFragmentLinksAreAccepted() {
        assertEquals(token, pairingToken("https://controller.example.invalid/pair#v=1&token=$token"))
        for (link in listOf(
            "http://controller.example.invalid/pair#v=1&token=$token",
            "https://controller.example.invalid.evil.test/pair#v=1&token=$token",
            "https://evil@controller.example.invalid/pair#v=1&token=$token",
            "https://controller.example.invalid:444/pair#v=1&token=$token",
            "https://controller.example.invalid/pair?token=$token#v=1",
            "https://controller.example.invalid/pair#v=2&token=$token",
            "https://controller.example.invalid/pair#v=1&token=$token&token=$token"
        )) assertThrows(IllegalArgumentException::class.java) { pairingToken(link) }
    }
    @Test fun onlyMqttFieldsChangeAndUnrelatedSecretsRemainPrivate() {
        val old = AutomationConfig(deviceId = "M11", captureToken = "dummy_private-capture", token = "dummy_local-http-token-1234567890",
            screenWidth = 1080, screenHeight = 2400, mode = "keyboard", httpPort = 8766)
        val config = buildJsonObject {
            put("uri", broker); put("device_id", "M11"); put("client_id", "aiphone-M11")
            put("username", "phone-" + "a".repeat(32)); put("password", token)
            put("topic_prefix", "aiphonekvm/" + "b".repeat(32) + "/" + "c".repeat(32) + "/")
        }
        val next = enrollmentConfig(old, config)
        assertEquals(old.captureToken, next.captureToken); assertEquals(old.token, next.token)
        assertEquals(old.screenWidth, next.screenWidth); assertEquals(old.screenHeight, next.screenHeight)
        assertEquals(old.mode, next.mode); assertEquals(old.httpPort, next.httpPort)
        assertEquals(token, next.mqttPassword)
        for ((field, value) in listOf("uri" to "tcp://evil.test:1883", "device_id" to "OTHER", "topic_prefix" to "#", "client_id" to "aiphone-OTHER")) {
            assertThrows(IllegalArgumentException::class.java) { enrollmentConfig(old, JsonObject(config + (field to JsonPrimitive(value)))) }
        }
    }
    @Test fun rollbackAndRecoveryKeepNewerLocalSettings() {
        val saved = AutomationConfig(deviceId = "M11", mqttUri = broker, mqttUsername = "old-phone", mqttPassword = "old-password")
        val current = saved.copy(screenWidth = 1440, screenHeight = 3200, httpPort = 8888,
            token = "dummy_new-http-token-123456789012345", captureToken = "dummy_new-capture-token", mqttUsername = "staged-phone")
        val restored = storedMqttProfile(current, saved.json(true))
        assertEquals(saved.mqttUsername, restored.mqttUsername)
        assertEquals(JsonObject(current.json(true).filterKeys { it != "mqtt" }), JsonObject(restored.json(true).filterKeys { it != "mqtt" }))
        assertEquals("无法连接网站，请检查网络后重试", enrollmentMessage(java.net.SocketTimeoutException("private diagnostics")))
    }

    @Test fun selfHostedQrMustMatchSelectedOriginIncludingPort() {
        val origin = "https://control.example.org:9443"
        assertEquals(token, pairingToken("$origin/pair#v=1&token=$token", origin))
        assertEquals(token, pairingToken("https://controller.example.invalid:443/pair#v=1&token=$token"))
        for (site in listOf("https://other.example.org:9443", "https://control.example.org", WEBSITE_ORIGIN)) {
            assertThrows(IllegalArgumentException::class.java) { pairingToken("$site/pair#v=1&token=$token", origin) }
        }
    }

    @Test fun selfHostedBrokerKeepsTlsAndDeviceRoleChecks() {
        val old = AutomationConfig(deviceId = "LOCAL")
        val connection = buildJsonObject {
            put("uri", "ssl://broker.example.org:8883"); put("device_id", old.deviceId)
            put("client_id", "aiphone-LOCAL"); put("username", "phone-" + "a".repeat(32))
            put("password", token); put("topic_prefix", "aiphonekvm/" + "b".repeat(32) + "/" + "c".repeat(32) + "/")
        }
        assertEquals("ssl://broker.example.org:8883", enrollmentConfig(old, connection).mqttUri)
        for (uri in listOf("tcp://broker.example.org:1883", "ssl://u:p@broker.example.org:8883", "ssl://broker.example.org:8883/path", "ssl://broker.example.org", "ssl://broker.example.org:70000")) {
            assertThrows(IllegalArgumentException::class.java) { enrollmentConfig(old, JsonObject(connection + ("uri" to JsonPrimitive(uri)))) }
        }
    }

    @Test fun recoveryUsesJournalSiteAndLegacyJournalsKeepOriginalSite() {
        assertEquals(WEBSITE_ORIGIN, journalOrigin(buildJsonObject { }))
        assertEquals("https://own.example:9443", journalOrigin(buildJsonObject { put("origin", "https://own.example:9443/") }))
        assertThrows(IllegalArgumentException::class.java) { journalOrigin(buildJsonObject { put("origin", "http://own.example") }) }
    }

}
