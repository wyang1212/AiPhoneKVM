// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.atharok.btremote.automation.HomeModuleOrder
import com.atharok.btremote.automation.PhoneControlMode
import com.atharok.btremote.ui.screens.ControlHomeScreen
import org.junit.*
import org.junit.Assert.*

class HomeReorderTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("home_reorder_test", Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }
    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun equalCardsEditButtonAndNoAccidentalModuleOrDeviceAction() {
        var modules=0;var devices=0
        compose.setContent { MaterialTheme { ControlHomeScreen("测试", "00:11:22:33:44:55",true,
            {modules++},{devices++},{},{},layoutPreferences=prefs) } }
        val ai=compose.onNodeWithContentDescription("AI 自动化").fetchSemanticsNode().boundsInRoot
        val xbox=compose.onNodeWithContentDescription("Xbox 手柄").fetchSemanticsNode().boundsInRoot
        assertEquals(xbox.width,ai.width,1f);assertEquals(xbox.height,ai.height,1f)
        val edit=compose.onNodeWithContentDescription("编辑图标").fetchSemanticsNode().boundsInRoot
        val settings=compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        assertTrue(edit.right<=settings.left+1f)
        compose.onNodeWithContentDescription("编辑图标").performClick()
        compose.onNodeWithContentDescription("AI 自动化").performClick()
        compose.onNodeWithContentDescription("蓝牙设备").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0,modules);assertEquals(0,devices) }
        compose.onNodeWithContentDescription("完成编辑").performClick()
        compose.onNodeWithContentDescription("AI 自动化").performClick()
        compose.runOnIdle { assertEquals(1,modules) }
    }

    @Test fun dragAcrossRowsPersistsAcrossReentryAndBackExitsEditing() {
        val visible=mutableStateOf(true);var closes=0;val modules=mutableListOf<PhoneControlMode>()
        compose.setContent { MaterialTheme { if (visible.value) ControlHomeScreen("测试","00:11:22:33:44:55",true,
            {modules.add(it)},{},{},{closes++},layoutPreferences=prefs) } }
        compose.onNodeWithContentDescription("编辑图标").performClick()
        val start=compose.onNodeWithContentDescription("AI 自动化").fetchSemanticsNode().boundsInRoot.center
        val destination=compose.onNodeWithContentDescription("键鼠").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithContentDescription("AI 自动化").performTouchInput {
            down(center);moveBy(Offset(15f,0f));moveTo(center+(destination-start),delayMillis=800);advanceEventTime(400);up()
        }
        compose.waitUntil(5000) { HomeModuleOrder.decode(prefs.getString("module_order",null)).first()!=PhoneControlMode.AI }
        val saved=HomeModuleOrder.decode(prefs.getString("module_order",null))
        assertEquals(HomeModuleOrder.defaults.toSet(),saved.toSet())
        compose.runOnIdle { assertTrue(modules.isEmpty()) }
        Espresso.pressBack();compose.waitForIdle()
        compose.onNodeWithContentDescription("编辑图标").assertExists()
        compose.runOnIdle { assertEquals(0,closes);visible.value=false }
        compose.waitForIdle();compose.runOnIdle { visible.value=true };compose.waitForIdle()
        val first=compose.onNodeWithContentDescription(saved.first().title).fetchSemanticsNode().boundsInRoot
        val ai=compose.onNodeWithContentDescription("AI 自动化").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top<ai.top || first.left<ai.left)
        compose.onNodeWithContentDescription(saved.first().title).performClick()
        compose.runOnIdle { assertEquals(saved.first(),modules.last()) }
    }
    @Test fun cancelledDragKeepsAllModulesAndSmallViewportStillScrolls() {
        var modules=0
        compose.setContent { MaterialTheme { Box(Modifier.height(480.dp)) {
            ControlHomeScreen("测试","00:11:22:33:44:55",true,
                {modules++},{},{},{},layoutPreferences=prefs)
        } } }
        compose.onNodeWithContentDescription("编辑图标").performClick()
        val card=compose.onNodeWithContentDescription("AI 自动化")
        val bounds=card.fetchSemanticsNode().boundsInRoot
        val grid=compose.onNodeWithTag("home-modules").fetchSemanticsNode().boundsInRoot
        card.performTouchInput {
            down(center);moveBy(Offset(0f,20f));moveTo(Offset(center.x,grid.bottom-bounds.top-8f),delayMillis=1000)
        }
        card.performTouchInput { cancel() }
        compose.waitForIdle()
        val result=HomeModuleOrder.decode(prefs.getString("module_order",null))
        assertEquals(8,result.size);assertEquals(HomeModuleOrder.defaults.toSet(),result.toSet())
        compose.onNodeWithTag("home-modules").performScrollToKey("TV")
        compose.onNodeWithContentDescription("TV").assertIsDisplayed()
        compose.onNodeWithContentDescription("完成编辑").performClick()
        compose.runOnIdle { assertEquals(0,modules) }
        compose.onNodeWithContentDescription("TV").performClick()
        compose.runOnIdle { assertEquals(1,modules) }
    }

}
