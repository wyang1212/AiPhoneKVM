// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.atharok.btremote.automation.WebsitePairingActivity
import org.junit.*
import org.junit.Assert.*

/** Runs on an isolated Android test installation; never binds to a public service. */
class WebsiteSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<WebsitePairingActivity>()
    private val prefs get() = compose.activity.getSharedPreferences("website_binding_private", Context.MODE_PRIVATE)
    private var original: Map<String, *> = emptyMap<String, Any?>()
    @Before fun snapshot() { original = prefs.all.toMap() }
    @After fun restore() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val editor = prefs.edit().clear()
        original.forEach { (key, value) -> editor.putString(key, value as String) }
        assertTrue(editor.commit())
    }
    @Test fun savedSiteSurvivesActivityRecreationAndRejectsHttp() {
        compose.onNodeWithText("网站地址（HTTPS）").performTextReplacement("https://selfhost.example:9443/")
        compose.onNodeWithText("保存网站地址").performClick()
        compose.waitUntil(10000) { prefs.getString("origin", "") == "https://selfhost.example:9443" }
        compose.onNodeWithText("当前网站：https://selfhost.example:9443").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("网站地址（HTTPS）").assertTextContains("https://selfhost.example:9443")
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(compose.activity.cacheDir, "website-settings.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("网站地址（HTTPS）").performTextReplacement("http://selfhost.example")
        compose.onNodeWithText("保存网站地址").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("请输入完整 HTTPS 网站地址，可含端口，不含路径、账号或参数").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("请输入完整 HTTPS 网站地址，可含端口，不含路径、账号或参数").assertExists()
        assertEquals("https://selfhost.example:9443", prefs.getString("origin", ""))
    }
    @Test fun pendingPairingLocksSiteAndKeepsRecoveryVisible() {
        assertTrue(prefs.edit().putString("pending", "{}").commit())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("网站地址（HTTPS）").assertIsNotEnabled()
        compose.onNodeWithText("保存网站地址").assertIsNotEnabled()
        compose.onNodeWithText("恢复上次配对").assertExists()
        compose.onNodeWithText("扫描网站二维码").assertDoesNotExist()
    }
}
