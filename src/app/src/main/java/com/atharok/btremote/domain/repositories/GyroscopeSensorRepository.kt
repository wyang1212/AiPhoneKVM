package com.atharok.btremote.domain.repositories

import kotlinx.coroutines.flow.StateFlow

interface GyroscopeSensorRepository {

    fun startListening()

    fun stopListening()

    fun getDisplayRotation(): Int

    fun getPositions(): StateFlow<Triple<Float, Float, Float>>
}