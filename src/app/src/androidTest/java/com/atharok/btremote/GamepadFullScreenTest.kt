// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.atharok.btremote.common.extensions.setFullScreen
import com.atharok.btremote.ui.components.GamepadFullScreen
import com.atharok.btremote.ui.screens.StatelessRemoteScreen
import com.atharok.btremote.ui.views.*
import org.junit.*
import org.junit.Assert.*

class GamepadFullScreenTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun bothGamepadsFillContentAndAllModulesHaveNoToolbar() {
        val mode=mutableStateOf(ControlMode.GAMEPAD)
        compose.setContent { MaterialTheme {
            StatelessRemoteScreen(true,mode.value,
                modeContent={GamepadControlPanel({true},Modifier.fillMaxSize(),fc=mode.value==ControlMode.FC)},
                originalActions={},remoteLayout={},navigationLayout={},overlayView={})
        } }
        for ((value,label) in listOf(ControlMode.GAMEPAD to "Xbox",ControlMode.FC to "FC")) {
            compose.runOnIdle { mode.value=value }
            val node=compose.onNodeWithContentDescription("$label 手柄操作区");node.assertIsDisplayed()
            val canvas=node.fetchSemanticsNode().boundsInRoot;val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("Gamepad fills available height",canvas.height>=root.height*.98f)
            compose.onNodeWithContentDescription("主页").assertDoesNotExist()
            compose.onNodeWithContentDescription("设置").assertDoesNotExist()
            compose.onNodeWithContentDescription("断开").assertDoesNotExist()
            compose.onNodeWithText("Xbox").assertDoesNotExist();compose.onNodeWithText("FC").assertDoesNotExist()
        }
        for (value in ControlMode.entries) {
            compose.runOnIdle { mode.value=value }
            for (label in listOf("主页", "键鼠", "键盘", "媒体", "鼠标", "TV", "设置", "断开")) {
                compose.onNodeWithContentDescription(label).assertDoesNotExist()
            }
        }
    }
    private fun restoresPreference(restore:Boolean) {
        val enabled=mutableStateOf(false);lateinit var view:android.view.View;lateinit var context:android.content.Context
        compose.setContent { MaterialTheme { view=LocalView.current;context=LocalContext.current;GamepadFullScreen(enabled.value,restore);Box(Modifier.fillMaxSize()) } }
        compose.runOnIdle { context.setFullScreen(restore);enabled.value=true }
        fun waitVisible(visible:Boolean) { compose.waitUntil(7000) {
            val insets=ViewCompat.getRootWindowInsets(view)
            insets?.isVisible(WindowInsetsCompat.Type.statusBars())==visible && insets.isVisible(WindowInsetsCompat.Type.navigationBars())==visible
        } }
        waitVisible(false)
        compose.runOnIdle { enabled.value=false };waitVisible(!restore)
    }
    @Test fun leavingRestoresVisibleSystemBars()=restoresPreference(false)
    @Test fun leavingPreservesUserFullScreenPreference()=restoresPreference(true)
}
