package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.atharok.btremote.common.extensions.parcelable
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.checkBluetoothScanPermission
import com.atharok.btremote.domain.entities.DeviceEntity

class BluetoothScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {
    val scannedDevices = mutableStateListOf<DeviceEntity>()

    // ---- BroadcastReceiver ----

    private val bluetoothScannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    intent.parcelable(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.let { device: BluetoothDevice ->
                        if(checkBluetoothConnectPermission(this@BluetoothScanner.context)) {
                            if(!scannedDevices.any { it.macAddress == device.address }) {
                                scannedDevices.add(
                                    DeviceEntity(
                                        name = device.name ?: "null",
                                        macAddress = device.address ?: "null",
                                        category = device.bluetoothClass.majorDeviceClass
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun registerBluetoothScannerReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(bluetoothScannerReceiver, filter)
    }

    fun unregisterBluetoothScannerReceiver() {
        try {
            context.unregisterReceiver(bluetoothScannerReceiver)
        } catch (e: java.lang.RuntimeException) {
            Log.e("unregisterReceiver()", "Receiver already unregister: ${e.message ?: e.toString()}")
        }
    }

    fun startDiscoveryDevices(): Boolean {
        return if (checkBluetoothScanPermission(context)) {
            adapter?.startDiscovery() == true
        } else false
    }

    fun cancelDiscoveryDevices(): Boolean {
        return if (checkBluetoothScanPermission(context)) {
            adapter?.cancelDiscovery() == true
        } else false
    }
}