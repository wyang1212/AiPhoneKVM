// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.atharok.btremote.automation.PhoneControlMode
import com.atharok.btremote.automation.HomeModuleOrder
import androidx.test.core.app.ApplicationProvider
import com.atharok.btremote.common.utils.*
import com.atharok.btremote.domain.entities.remoteInput.*
import com.atharok.btremote.ui.screens.ControlHomeScreen
import com.atharok.btremote.ui.views.GamepadControlPanel
import org.junit.*
import org.junit.Assert.*
import java.util.Collections
import kotlin.math.min

class HomeFcLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun homeModulesAndBottomDeviceSelectionAreDistinctActions() {
        val modules=mutableListOf<PhoneControlMode>();var choices=0
        compose.setContent { MaterialTheme { ControlHomeScreen("测试手机","00:11:22:33:44:55",true,
            { modules.add(it) },{ choices++ },{}, {}) } }
        for ((label,mode) in listOf("AI 自动化" to PhoneControlMode.AI,"Xbox 手柄" to PhoneControlMode.XBOX,
            "FC 红白机" to PhoneControlMode.FC,"键鼠" to PhoneControlMode.KEY_MOUSE,
            "键盘" to PhoneControlMode.KEYBOARD,"媒体" to PhoneControlMode.MEDIA,
            "鼠标" to PhoneControlMode.MOUSE,"TV" to PhoneControlMode.TV)) {
            // Lazy precomposed nodes can expose placeholder bounds before actual placement.
            // Scroll by the grid's stable key, then exercise a real touch rather than OnClick.
            compose.onNodeWithTag("home-modules").performScrollToKey(mode.name)
            val count=modules.size
            compose.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(count+1,modules.size);assertEquals(mode,modules.last()) }
        }
        compose.onNodeWithContentDescription("手动遥控").assertDoesNotExist()
        val footer=compose.onNodeWithContentDescription("蓝牙设备");footer.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1,choices);assertEquals(8,modules.size) }
        compose.onNodeWithText("当前：测试手机").assertIsDisplayed()
    }
    @Test fun emptyAndRemovedSelectionHaveExplicitGuidance() {
        val address=mutableStateOf("")
        compose.setContent { MaterialTheme { ControlHomeScreen(null,address.value,true,{},{},{},{}) } }
        compose.onNodeWithText("尚未选择 · 点击查看已配对设备").assertIsDisplayed()
        compose.runOnIdle { address.value="00:11:22:33:44:55" }
        compose.onNodeWithText("原设备未配对，请重新选择").assertIsDisplayed()
    }
    @Test fun reorderedModulesRouteCorrectlyWhenScrollingBothWays() {
        val prefs=ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("home_fc_route_test",Context.MODE_PRIVATE)
        val defaults=HomeModuleOrder.defaults
        val reordered=defaults.drop(6)+defaults.take(6)
        prefs.edit().putString("module_order",HomeModuleOrder.encode(reordered)).commit()
        try {
            val events=mutableListOf<PhoneControlMode>()
            // Use the production home and a saved order that differs from list indices.
            compose.setContent { MaterialTheme { ControlHomeScreen("测试手机","00:11:22:33:44:55",true,
                { events.add(it) },{},{},{},layoutPreferences=prefs) } }
            for (mode in defaults+defaults.reversed()) {
                compose.onNodeWithTag("home-modules").performScrollToKey(mode.name)
                val count=events.size
                compose.onNodeWithContentDescription(mode.title).assertIsDisplayed().performClick()
                compose.runOnIdle { assertEquals(count+1,events.size);assertEquals(mode,events.last()) }
            }
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun fcMultiTouchAndLeavingReleaseEveryInput() {
        val reports=Collections.synchronizedList(mutableListOf<ByteArray>());val visible=mutableStateOf(true)
        compose.setContent { MaterialTheme { if(visible.value) GamepadControlPanel({reports.add(it.copyOf());true},Modifier.fillMaxSize(),fc=true) } }
        val node=compose.onNodeWithContentDescription("FC 手柄操作区")
        fun TouchInjectionScope.p(label:String,dx:Float=0f,dy:Float=0f):Offset {
            val c=FcGamepadLayout.controls.single { it.label==label };val s=min(width/GamepadLayout.WIDTH,height/GamepadLayout.HEIGHT)
            return Offset((width-GamepadLayout.WIDTH*s)/2+(c.x+dx)*s,(height-GamepadLayout.HEIGHT*s)/2+(c.y+dy)*s)
        }
        node.performTouchInput {
            down(0,p("方向键"));moveTo(0,p("方向键",50f,-50f));down(1,p("A"));down(2,p("B"));advanceEventTime(100);up(1);cancel()
        }
        compose.runOnIdle {
            assertTrue(reports.any { it[0].toInt() and 3 == 3 && it[2].toInt()==1 })
            assertTrue(reports.any { it[0].toInt() and 3 == 2 && it[2].toInt()==1 })
            assertArrayEquals(GamepadReport.neutral(),reports.last())
        }
        node.performTouchInput { down(0,p("START")) }
        compose.runOnIdle { visible.value=false };compose.waitForIdle()
        assertArrayEquals(GamepadReport.neutral(),reports.last())
    }
}
