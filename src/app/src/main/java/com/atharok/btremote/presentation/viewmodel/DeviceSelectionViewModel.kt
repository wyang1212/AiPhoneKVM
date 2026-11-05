package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.common.extensions.capitalizeFirstChar
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.resources.Result
import com.atharok.btremote.domain.usecases.DeviceSelectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class DeviceSelectionViewModel(
    private val useCase: DeviceSelectionUseCase
): ViewModel() {

    // ---- Connection ----

    fun connectDevice(macAddress: String): Boolean = useCase.connectDevice(macAddress)

    fun disconnectDevice(): Boolean = useCase.disconnectDevice()

    // ---- Get Bonded Devices ----

    private val _devicesFlow: MutableStateFlow<List<DeviceEntity>> = MutableStateFlow(listOf())
    val devicesFlow: StateFlow<List<DeviceEntity>> = _devicesFlow

    fun findBondedDevices(
        context: CoroutineContext = EmptyCoroutineContext
    ) = viewModelScope.launch(context = context) {
        _devicesFlow.value = useCase.getBondedDevices().sortedBy { it.name.capitalizeFirstChar() }
    }

    // ---- Unpair device ----

    fun unpairDevice(address: String?): Result<Boolean> {
        val result: Result<Boolean> = useCase.unpairDevice(address)
        if(result.value) {
            runBlocking { delay(200L) } // -> Small delay to allow the system to complete updating the device removal.
            findBondedDevices(context = Dispatchers.Main)
        }
        return result
    }

    // ---- Settings ----

    fun getFavoriteDevices(): Flow<List<String>> = useCase.getFavoriteDevices()
    fun saveFavoriteDevices(macAddresses: List<String>) = viewModelScope.launch {
        useCase.saveFavoriteDevices(macAddresses)
    }

    fun getAutoConnectDeviceAddressFlow(): Flow<String> = useCase.getAutoConnectDeviceAddressFlow()
    fun saveAutoConnectDeviceAddress(address: String) = viewModelScope.launch {
        useCase.saveAutoConnectDeviceAddress(address)
    }
}