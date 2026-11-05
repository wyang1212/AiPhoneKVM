// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.domain.entities

import android.bluetooth.BluetoothHidDevice

data class DeviceHidConnectionState(
    val state: Int = BluetoothHidDevice.STATE_DISCONNECTED,
    val deviceName: String = "",
    val deviceAddress: String = ""
)