package com.atharok.btremote.domain.usecases

import com.atharok.btremote.domain.repositories.DataStoreRepository
import kotlinx.coroutines.flow.Flow

class BluetoothActivationUseCase(
    private val dataStoreRepository: DataStoreRepository
) {
    fun hideBluetoothActivationButton(): Flow<Boolean> = dataStoreRepository.hideBluetoothActivationButton()
}