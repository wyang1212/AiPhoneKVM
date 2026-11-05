// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.automation.AutomationRuntime
import com.atharok.btremote.automation.AutomationScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Layout navigation must retain drafts without saving configuration or starting a task. */
@RunWith(AndroidJUnit4::class)
class AutomationLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var runtime: AutomationRuntime
    @Before fun setup() {
        runtime=AutomationRuntime.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runtime.running.value=false
        compose.setContent { MaterialTheme { AutomationScreen(runtime, {}, "02:00:00:00:00:01") } }
    }
    @After fun cleanup() { runtime.running.value=false }
    @Test fun taskFirstAndAdvancedContentCollapsed() {
        compose.onNodeWithText("任务").assertIsSelected()
        compose.onNodeWithText("任务目标").assertIsDisplayed()
        compose.onNodeWithText("开始自动执行").assertIsNotEnabled()
        compose.onNodeWithText("最多操作步数").assertDoesNotExist()
        compose.onNodeWithText("Ollama 兼容接口地址").assertDoesNotExist()
        compose.onNodeWithText("BlueHIDFlow JSON 命令").assertDoesNotExist()
    }
    @Test fun draftsSurviveTabsWithoutSavingOrStarting() {
        val config=runtime.store.state.value
        val originalModel=runtime.vision.model.value
        compose.onNodeWithText("任务目标").performTextInput("layout acceptance draft")
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("Ollama 兼容接口地址").performTextReplacement("https://example.invalid/v1")
        compose.onNodeWithText("设备").performClick()
        compose.onNodeWithText("连接被控端").assertExists()
        compose.onNodeWithText("任务目标").assertDoesNotExist()
        compose.onNodeWithText("任务").performClick()
        compose.onNodeWithText("layout acceptance draft").assertExists()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("https://example.invalid/v1").assertExists()
        assertEquals(config,runtime.store.state.value)
        assertEquals(originalModel,runtime.vision.model.value)
        assertFalse(runtime.goalLoop.active)
        assertFalse(runtime.running.value)
    }
    @Test fun tabsPreserveServiceAndHidePreviouslyRevealedToken() {
        compose.runOnIdle { runtime.running.value=true }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("查看令牌").performScrollTo().performClick()
        compose.onNodeWithText(runtime.store.state.value.token).assertExists()
        compose.onNodeWithText("任务").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText(runtime.store.state.value.token).assertDoesNotExist()
        assertTrue(runtime.running.value)
        assertFalse(runtime.goalLoop.active)
    }
    @Test fun websiteTabHasSettingsEntryAndDoesNotStartTasks() {
        val config = runtime.store.state.value
        compose.onNodeWithText("网站").performClick()
        compose.onNodeWithText("网站").assertIsSelected()
        compose.onNodeWithText("网站设置 / 扫码").assertIsDisplayed()
        compose.onNodeWithText("网站远程连接").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir, "website-tab.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(config, runtime.store.state.value)
        assertFalse(runtime.goalLoop.active)
        assertFalse(runtime.running.value)
    }
}
