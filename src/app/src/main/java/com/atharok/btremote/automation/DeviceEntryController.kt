// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.bluetooth.BluetoothProfile
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

enum class PhoneControlMode(val title: String) {
    AI("AI 自动化"), MANUAL("手动遥控"), XBOX("Xbox 手柄"), FC("FC 红白机"),
    KEY_MOUSE("键鼠"), KEYBOARD("键盘"), MEDIA("媒体"), MOUSE("鼠标"), TV("TV")
}

/** Names are not unique. A session is valid only for its explicitly selected Bluetooth address. */
internal fun DeviceHidConnectionState.isConnectedTo(address: String): Boolean =
    address.isNotBlank() && state == BluetoothProfile.STATE_CONNECTED && deviceAddress.equals(address, true)

/** Called only after an explicit module click; an unsolicited host connection never enters a mode. */
class DeviceEntryController(
    private val state: StateFlow<DeviceHidConnectionState>,
    private val connect: (String) -> Boolean,
    private val disconnect: () -> Boolean,
    private val timeoutMs: Long = 20000
) {
    suspend fun select(address: String) {
        require(address.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) { "设备地址无效" }
        if (state.value.isConnectedTo(address)) return
        var initiated = false
        try {
            withTimeout(timeoutMs) {
                val current = state.value
                if (current.state == BluetoothProfile.STATE_CONNECTING && current.deviceAddress.equals(address, true)) {
                    state.first { it.isConnectedTo(address) }
                    return@withTimeout
                }
                if (current.state != BluetoothProfile.STATE_DISCONNECTED) {
                    check(disconnect()) { "未能断开原设备，请稍后重试" }
                    state.first { it.state == BluetoothProfile.STATE_DISCONNECTED }
                }
                initiated = true
                check(connect(address) || state.value.isConnectedTo(address)) {
                    "连接未建立，请从被控设备的蓝牙设置主动连接本机后，再点选该设备"
                }
                state.first { it.isConnectedTo(address) }
            }
        } catch (error: Exception) {
            val current = state.value
            if (initiated && current.deviceAddress.equals(address, true)) disconnect()
            throw error
        }
    }
}
