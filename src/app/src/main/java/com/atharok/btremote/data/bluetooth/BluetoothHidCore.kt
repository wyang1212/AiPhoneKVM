package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.Context
import androidx.annotation.RequiresPermission
import com.atharok.btremote.common.utils.ConsumerReportCompatibility
import com.atharok.btremote.common.utils.GamepadReport
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.isMacAddress
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardTextSender
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class BluetoothHidCore(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val hidSettings: BluetoothHidDeviceAppSdpSettings
) {
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    @Volatile private var lastGamepadReport: ByteArray = GamepadReport.neutral()

    private val _isBluetoothServiceRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isBluetoothServiceRunning: StateFlow<Boolean> = _isBluetoothServiceRunning

    private val _isBluetoothHidProfileRegistered: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isBluetoothHidProfileRegistered: StateFlow<Boolean> = _isBluetoothHidProfileRegistered

    private var autoConnectDeviceAddress: String = ""

    // ---- HID Profile ----

    fun startBluetoothHidProfile(autoConnectDeviceAddress: String) {
        this.autoConnectDeviceAddress = autoConnectDeviceAddress
        adapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun stopBluetoothHidProfile() {
        bluetoothHidDevice?.let { hidDevice ->
            if (checkBluetoothConnectPermission(context)) {
                disconnectDevice(hidDevice)
                hidDevice.unregisterApp()
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
        bluetoothHidDevice = null
        _deviceHidConnectionState.value = DeviceHidConnectionState()
        _isBluetoothHidProfileRegistered.value = false
        _isBluetoothServiceRunning.value = false
    }

    // ---- BluetoothProfile.ServiceListener implementation ----

    private val serviceListener = object : ServiceListener {
        override fun onServiceConnected(i: Int, bluetoothProfile: BluetoothProfile?) {
            if(i == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = bluetoothProfile as? BluetoothHidDevice?
                bluetoothHidDevice?.let { hidDevice ->
                    if (checkBluetoothConnectPermission(context)) {
                        // The return value only acknowledges the request; readiness comes from onAppStatusChanged.
                        hidDevice.registerApp(hidSettings, null, null, Runnable::run, callback)
                    }
                }
            }
            _isBluetoothServiceRunning.value = true
        }

        override fun onServiceDisconnected(i: Int) {
            if (i != BluetoothProfile.HID_DEVICE) return
            bluetoothHidDevice = null
            bluetoothDevice = null
            _isBluetoothHidProfileRegistered.value = false
            _isBluetoothServiceRunning.value = false
            _deviceHidConnectionState.value = DeviceHidConnectionState()
        }
    }

    // ---- BluetoothHidDevice.Callback implementation ----

    private val callback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            _isBluetoothHidProfileRegistered.value = registered
            if(registered && isMacAddress(autoConnectDeviceAddress)) {
                connectDevice(autoConnectDeviceAddress)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (checkBluetoothConnectPermission(context)) {
                // Hosts may initiate the connection without calling connectDevice() in this app.
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    bluetoothDevice = device
                    lastGamepadReport = GamepadReport.neutral()
                    if (device != null) bluetoothHidDevice?.let { sendAndroidReport(it, device, GamepadReport.ID, lastGamepadReport) }
                } else if (state == BluetoothProfile.STATE_DISCONNECTED && bluetoothDevice?.address == device?.address) {
                    bluetoothDevice = null
                    lastGamepadReport = GamepadReport.neutral()
                }
                _deviceHidConnectionState.value = DeviceHidConnectionState(state, device?.name ?: "", device?.address ?: "")
            }
        }
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (device == null || id.toInt() != GamepadReport.ID || !checkBluetoothConnectPermission(context)) return
            val hid = bluetoothHidDevice ?: return
            if (type != BluetoothHidDevice.REPORT_TYPE_INPUT || (bufferSize != 0 && bufferSize < GamepadReport.SIZE)) {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
            } else hid.replyReport(device, type, id, lastGamepadReport.copyOf())
        }
    }

    // ---- Device Connection ----

    private var bluetoothDevice: BluetoothDevice? = null

    fun connectDevice(deviceAddress: String): Boolean {
        if (checkBluetoothConnectPermission(context)) {
            bluetoothHidDevice?.let {
                return connectDevice(it, deviceAddress)
            }
        }
        return false
    }

    fun disconnectDevice(): Boolean {
        if (checkBluetoothConnectPermission(context)) {
            bluetoothHidDevice?.let {
                return disconnectDevice(it)
            }
        }
        return false
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun connectDevice(hidDevice: BluetoothHidDevice, deviceAddress: String): Boolean {
        var isConnected = false
        bluetoothDevice = adapter?.getRemoteDevice(deviceAddress.uppercase(Locale.getDefault()))
        bluetoothDevice?.let {
            isConnected = hidDevice.connect(it)
        }
        return isConnected
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun disconnectDevice(hidDevice: BluetoothHidDevice): Boolean {
        var isDisconnected = false
        bluetoothDevice?.let {
            sendAndroidReport(hidDevice, it, GamepadReport.ID, GamepadReport.neutral())
            lastGamepadReport = GamepadReport.neutral()
            // disconnect() is called twice because, during my tests on a Pixel 3a, the state
            // changes to DISCONNECTING but never to DISCONNECTED if the call is made only once.
            // This error occurs only on this device and not on the others I tested. It seems to
            // be due to a bug in the library or the device.
            hidDevice.disconnect(it)
            isDisconnected = hidDevice.disconnect(it)
        }
        bluetoothDevice = null
        return isDisconnected
    }

    // ---- StateFlow ----

    private val _deviceHidConnectionState: MutableStateFlow<DeviceHidConnectionState> = MutableStateFlow(
        DeviceHidConnectionState()
    )
    val deviceHidConnectionState: StateFlow<DeviceHidConnectionState> = _deviceHidConnectionState

    // ---- Send Report ----

    private fun sendAndroidReport(
        hidDevice: BluetoothHidDevice,
        device: BluetoothDevice,
        id: Int,
        bytes: ByteArray
    ): Boolean {
        return try {
            if (checkBluetoothConnectPermission(context)) {
                if (id == GamepadReport.ID && bytes.size != GamepadReport.SIZE) return false
                val sent = hidDevice.sendReport(device, id, bytes)
                if (sent && id == GamepadReport.ID) lastGamepadReport = bytes.copyOf()
                sent
            } else false
        } catch (_: SecurityException) {
            false
        }
    }

    fun sendReport(id: Int, bytes: ByteArray, expectedAddress: String? = null): Boolean {
        var success = false
        bluetoothDevice?.let { device ->
            bluetoothHidDevice?.let { hidDevice ->
                if (checkBluetoothConnectPermission(context)) {
                    if (expectedAddress != null && !device.address.equals(expectedAddress, true)) return false
                    success = ConsumerReportCompatibility.route(id, bytes).fold(true) { allSent, report ->
                        val sent = sendAndroidReport(hidDevice, device, report.id, report.bytes)
                        allSent && sent
                    }
                }
            }
        }
        return success
    }

    suspend fun sendTextReport(
        text: String,
        virtualKeyboardLayout: VirtualKeyboardLayout,
        shouldSendEnter: Boolean
    ): Boolean {
        val device = bluetoothDevice ?: return false
        val hidDevice = bluetoothHidDevice ?: return false
        if (!checkBluetoothConnectPermission(context)) return false
        return KeyboardTextSender(output = { bytes ->
            sendAndroidReport(hidDevice, device, KEYBOARD_REPORT_ID, bytes)
        }).send(text, virtualKeyboardLayout::getKeyboardKey, shouldSendEnter)
    }
}
