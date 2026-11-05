package com.atharok.btremote.presentation.viewmodel

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.usecases.DeviceDiscoveryUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceDiscoveryViewModel(
    private val useCase: DeviceDiscoveryUseCase
): ViewModel() {

    // ---- Connection ----

    fun connectDevice(macAddress: String): Boolean = useCase.connectDevice(macAddress)

    fun disconnectDevice(): Boolean = useCase.disconnectDevice()

    // ---- Discover Devices ----

    val scannedDevices: SnapshotStateList<DeviceEntity> get() = useCase.getScannedDevices()

    fun registerBluetoothScannerReceiver() {
        useCase.registerBluetoothScannerReceiver()
    }

    fun unregisterBluetoothScannerReceiver() {
        useCase.unregisterBluetoothScannerReceiver()
    }

    private val _isDiscoveringFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isDiscoveringFlow: StateFlow<Boolean> = _isDiscoveringFlow

    private var startDiscoveryJob: Job? = null

    fun startDiscovery() {
        startDiscoveryJob?.cancel()
        useCase.cancelDiscovery()
        useCase.startDiscovery()
        _isDiscoveringFlow.value = true
        startDiscoveryJob = viewModelScope.launch {
            delay(12000L)
            cancelDiscovery()
        }
    }

    fun cancelDiscovery() {
        useCase.cancelDiscovery()
        _isDiscoveringFlow.value = false
    }
}