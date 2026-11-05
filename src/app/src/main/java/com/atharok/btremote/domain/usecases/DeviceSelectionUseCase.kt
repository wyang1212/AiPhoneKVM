package com.atharok.btremote.domain.usecases

import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.repositories.BluetoothRepository
import com.atharok.btremote.domain.repositories.DataStoreRepository
import com.atharok.btremote.domain.resources.Result
import kotlinx.coroutines.flow.Flow

class DeviceSelectionUseCase(
    private val bluetoothRepository: BluetoothRepository,
    private val dataStoreRepository: DataStoreRepository
) {

    // ---- Connection ----

    fun connectDevice(deviceAddress: String): Boolean {
        return bluetoothRepository.connectDevice(deviceAddress)
    }

    fun disconnectDevice(): Boolean {
        return bluetoothRepository.disconnectDevice()
    }

    // ---- Get Bonded Devices ----

    fun getBondedDevices(): List<DeviceEntity> = bluetoothRepository.getBondedDevices()

    fun unpairDevice(address: String?): Result<Boolean> = bluetoothRepository.unpairDevice(address)

    // ---- DataStore ----

    fun getFavoriteDevices(): Flow<List<String>> = dataStoreRepository.getFavoriteDevices()
    suspend fun saveFavoriteDevices(macAddresses: List<String>) {
        dataStoreRepository.saveFavoriteDevices(macAddresses)
    }

    fun getAutoConnectDeviceAddressFlow(): Flow<String> = dataStoreRepository.getAutoConnectDeviceAddressFlow()
    suspend fun saveAutoConnectDeviceAddress(address: String) {
        dataStoreRepository.saveAutoConnectDeviceAddress(address)
    }
}