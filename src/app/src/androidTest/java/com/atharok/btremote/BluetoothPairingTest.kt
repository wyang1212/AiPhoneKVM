// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.Manifest
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.atharok.btremote.ui.screens.ControlHomeScreen
import com.atharok.btremote.automation.PhoneControlMode
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.atharok.btremote.common.utils.bluetoothConnectPermissions
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class BluetoothPairingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requestsOnlyAfterRegisteredAndResumedAndDoesNotRepeatAfterRejectionOrExpiry() {
        var ready by mutableStateOf(false)
        var foreground by mutableStateOf(true)
        var visible by mutableStateOf(false)
        var calls = 0
        compose.setContent {
            BluetoothPairingAutoRequest(true, ready, false, visible, foreground) { calls++ }
        }
        compose.runOnIdle { assertEquals(0, calls); foreground = false; ready = true }
        compose.runOnIdle { assertEquals(0, calls); foreground = true }
        compose.runOnIdle { assertEquals(1, calls); foreground = false }
        compose.runOnIdle { foreground = true; ready = false }
        compose.runOnIdle { ready = true; visible = true }
        compose.runOnIdle { visible = false }
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun connectedHostSkipsPromptAndBluetoothRestartAllowsAnotherRequest() {
        var enabled by mutableStateOf(true)
        var connected by mutableStateOf(true)
        var calls = 0
        compose.setContent {
            BluetoothPairingAutoRequest(enabled, enabled, connected, false, true) { calls++ }
        }
        compose.runOnIdle { assertEquals(0, calls); connected = false }
        compose.runOnIdle { assertEquals(1, calls); enabled = false }
        compose.runOnIdle { enabled = true }
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun alreadyDiscoverableDoesNotPromptAgainAfterExpiry() {
        var visible by mutableStateOf(true)
        var calls = 0
        compose.setContent {
            BluetoothPairingAutoRequest(true, true, false, visible, true) { calls++ }
        }
        compose.runOnIdle { assertEquals(0, calls); visible = false }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun readinessStatusRetryAndIncomingConnectionStayInCard() {
        var ready by mutableStateOf(false)
        var visible by mutableStateOf(false)
        var requesting by mutableStateOf(false)
        var connection by mutableStateOf(DeviceHidConnectionState())
        var calls = 0
        compose.setContent { MaterialTheme {
            BluetoothPairingCard("测试键鼠", ready, connection,
                BluetoothPairingState(visible, requesting, null) { calls++ })
        } }
        compose.onNodeWithText("本机蓝牙名称：测试键鼠").assertIsDisplayed()
        compose.onNodeWithText("允许其他设备发现（5分钟）").assertIsNotEnabled()
        compose.runOnIdle { ready = true }
        compose.onNodeWithText("允许其他设备发现（5分钟）").performClick()
        compose.runOnIdle { assertEquals(1, calls); requesting = true }
        compose.onNodeWithText("允许其他设备发现（5分钟）").assertIsNotEnabled()
        compose.runOnIdle { requesting = false; visible = true }
        compose.onNodeWithText("延长可发现时间").assertIsEnabled()
        compose.runOnIdle {
            connection = DeviceHidConnectionState(BluetoothProfile.STATE_CONNECTED, "电脑", "AA:BB:CC:DD:EE:FF")
        }
        compose.onNodeWithText("蓝牙已连接：电脑").assertIsDisplayed()
        compose.onNodeWithText("在下方选择该设备，再点击功能开始控制。").assertIsDisplayed()
        compose.onNodeWithText("延长可发现时间").assertDoesNotExist()
    }

    @Test fun restoredPageDoesNotRepeatSystemPrompt() {
        var calls = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            BluetoothPairingAutoRequest(true, true, false, false, true) { calls++ }
        }
        compose.runOnIdle { assertEquals(1, calls) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun homeHasOneDeviceEntryAndKeepsSelectionDistinctFromConnection() {
        val modules = mutableListOf<PhoneControlMode>()
        var choices = 0
        compose.setContent { MaterialTheme {
            ControlHomeScreen("已选手机", "00:11:22:33:44:55", true, { modules.add(it) }, { choices++ }, {}, {},
                connectionStatus = "已连接：另一台电脑")
        } }
        compose.onNodeWithContentDescription("配对设备").assertDoesNotExist()
        compose.onNodeWithText("本机蓝牙名称：测试键鼠").assertDoesNotExist()
        compose.onNodeWithText("允许其他设备发现（5分钟）").assertDoesNotExist()
        compose.onNodeWithText("当前：已选手机").assertIsDisplayed()
        compose.onNodeWithText("已连接：另一台电脑").assertIsDisplayed()
        for (mode in PhoneControlMode.entries.filter { it != PhoneControlMode.MANUAL }) {
            compose.onNodeWithTag("home-modules").performScrollToKey(mode.name)
            compose.onNodeWithContentDescription(mode.title).assertIsDisplayed().performClick()
        }
        compose.onAllNodesWithContentDescription("蓝牙设备").assertCountEquals(1)
        compose.onNodeWithContentDescription("蓝牙设备").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(8, modules.size); assertEquals(8, modules.toSet().size); assertEquals(1, choices) }
    }

    @Test fun packagedAppDeclaresAdvertiseAndUsesKeyboardMouseSubclass() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions
        assertTrue(permissions?.contains(Manifest.permission.BLUETOOTH_ADVERTISE) == true)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertTrue(bluetoothConnectPermissions.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
            assertTrue(bluetoothConnectPermissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        }
        val settings = GlobalContext.get().get<BluetoothHidDeviceAppSdpSettings>()
        assertEquals(BluetoothHidDevice.SUBCLASS1_COMBO, settings.subclass)
    }
}
