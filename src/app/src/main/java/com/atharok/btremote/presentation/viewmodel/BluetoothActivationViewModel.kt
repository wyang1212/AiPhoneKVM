package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.atharok.btremote.domain.usecases.BluetoothActivationUseCase
import kotlinx.coroutines.flow.Flow

class BluetoothActivationViewModel(
    private val useCase: BluetoothActivationUseCase
): ViewModel() {
    val hideBluetoothActivationButton: Flow<Boolean> get() = useCase.hideBluetoothActivationButton()
}