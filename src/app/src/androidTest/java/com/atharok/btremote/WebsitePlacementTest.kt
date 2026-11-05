// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.automation.AutomationRuntime
import com.atharok.btremote.automation.AutomationScreen
import com.atharok.btremote.automation.PhoneControlMode
import com.atharok.btremote.ui.screens.ControlHomeScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WebsitePlacementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productionHomeHasNoWebsiteEntry() {
        compose.setContent { MaterialTheme {
            ControlHomeScreen(null, "", true, {}, {}, {}, {})
        } }
        compose.onNodeWithText("网站连接").assertDoesNotExist()
        compose.onNodeWithText("网站设置 / 扫码").assertDoesNotExist()
        compose.onNodeWithContentDescription("AI 自动化").assertIsDisplayed()
        compose.onNodeWithContentDescription("蓝牙设备").assertIsDisplayed()
    }

    @Test fun websiteEntryIsInsideAiAndDoesNotChangeBindingOrStartTask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("website_binding_private", android.content.Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val runtime = AutomationRuntime.get(context)
        var ai by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            if (ai) AutomationScreen(runtime, { ai = false }, "02:00:00:00:00:01")
            else ControlHomeScreen(null, "", true, { if (it == PhoneControlMode.AI) ai = true }, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("AI 自动化").performClick()
        compose.onNodeWithText("网站").performClick()
        compose.onNodeWithText("网站").assertIsSelected()
        compose.onNodeWithText("网站设置 / 扫码").assertIsDisplayed()
        compose.onNodeWithText("网站远程连接").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("Website binding changed", original == prefs.all)
            assertFalse(runtime.goalLoop.active)
        }
    }
}
