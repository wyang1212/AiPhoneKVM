// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atharok.btremote.ui.components.CheckBluetoothStateChanged
import com.atharok.btremote.ui.screens.BluetoothActivationScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothRefreshTest {
    @Before fun permission() { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName,android.Manifest.permission.BLUETOOTH_CONNECT) }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun resumeReadsActualAdapterWithoutNeedingANewBroadcast() {
        var actual=false;var observed by mutableStateOf(false)
        compose.setContent { CheckBluetoothStateChanged({actual}) { observed=it };Text(if(observed)"on" else "off") }
        compose.onNodeWithText("off").assertExists()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        actual=true
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("on").assertExists()
    }
    @Test fun alreadyOnButtonRefreshesInsteadOfOpeningEnableRequest() {
        var enabled by mutableStateOf(false);var navigated=false;var reads=0
        compose.setContent { MaterialTheme {
            BluetoothActivationScreen(enabled,refreshBluetoothState={reads++;enabled=true;true},
                navigateToBluetoothDeviceSelectionScreen={navigated=true},navigateToSettings={})
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.bluetooth_enabled_button)).performClick()
        compose.waitForIdle()
        assertEquals(1,reads);assertTrue(navigated)
        assertEquals(Lifecycle.State.RESUMED,compose.activity.lifecycle.currentState)
    }
}
