// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.automation.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises mode/lifecycle UI with synthetic state; hardware and optical acceptance are separate. */
@RunWith(AndroidJUnit4::class)
class CameraModeLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var runtime:AutomationRuntime
    @Before fun setup() {
        runtime=AutomationRuntime.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runtime.running.value=false;runtime.camera.stop(notify=false);runtime.selectCamera(false)
        compose.setContent { MaterialTheme { AutomationScreen(runtime,{},"02:00:00:00:00:01") } }
    }
    @After fun cleanup() {
        runtime.camera.stop(notify=false);runtime.running.value=false;runtime.selectCamera(false)
    }
    @Test fun selectingCameraKeepsDraftAndNeverOpensItWithoutConsent() {
        val before=runtime.store.state.value
        compose.onNodeWithText("任务目标").performTextInput("camera draft")
        compose.onNodeWithText("设备").performClick()
        compose.onNodeWithText("主控摄像头").performClick()
        assertTrue(runtime.cameraMode.value);assertFalse(runtime.camera.open.value)
        compose.onNodeWithText("开启摄像头").assertExists()
        compose.onNodeWithText("任务").performClick()
        compose.onNodeWithText("camera draft").assertExists()
        compose.onNodeWithText("请先到设备页开启摄像头并完成校准").assertExists()
        assertEquals(before,runtime.store.state.value)
    }
    @Test fun switchingBackRestoresCompanionSetupWithoutSavingConfiguration() {
        val before=runtime.store.state.value
        compose.onNodeWithText("设备").performClick()
        compose.onNodeWithText("主控摄像头").performClick()
        compose.onNodeWithText("截图助手").performClick()
        assertFalse(runtime.cameraMode.value);assertNull(runtime.capture.localProvider)
        compose.onNodeWithText("连接被控端").assertExists()
        assertEquals(before,runtime.store.state.value)
    }
    @Test fun backgroundClosesCameraAndInvalidatesCalibrationWithoutAutoReopening() {
        compose.onNodeWithText("设备").performClick()
        compose.onNodeWithText("主控摄像头").performClick()
        compose.runOnIdle { runtime.camera.open.value=true;runtime.camera.ready.value=true }
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue("A visible permission overlay must not erase calibration",runtime.camera.open.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(runtime.camera.open.value);assertFalse(runtime.camera.ready.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertFalse(runtime.camera.open.value)
    }
}
