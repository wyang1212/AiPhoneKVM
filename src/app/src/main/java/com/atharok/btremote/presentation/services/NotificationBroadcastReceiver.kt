package com.atharok.btremote.presentation.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.domain.usecases.BluetoothHidServiceUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationBroadcastReceiver: BroadcastReceiver(), KoinComponent {

    private val useCase: BluetoothHidServiceUseCase by inject()

    companion object {
        const val ACTION_VOLUME_UP = "action_volume_up"
        const val ACTION_VOLUME_DOWN = "action_volume_down"
        const val ACTION_MULTIMEDIA_PLAY_PAUSE = "action_multimedia_play_pause"
        const val ACTION_MULTIMEDIA_PREVIOUS = "action_multimedia_previous"
        const val ACTION_MULTIMEDIA_NEXT = "action_multimedia_next"
        const val ACTION_LEFT = "action_left"
        const val ACTION_RIGHT = "action_right"
        const val ACTION_UP = "action_up"
        const val ACTION_DOWN = "action_down"
        const val ACTION_PICK = "action_pick"
        const val ACTION_BACK = "action_back"
        const val ACTION_HOME = "action_home"
        const val ACTION_DISCONNECT = "action_disconnect"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            ACTION_VOLUME_UP -> sendReport(RemoteButtonProperties.VolumeUpButton.input)
            ACTION_VOLUME_DOWN -> sendReport(RemoteButtonProperties.VolumeDownButton.input)
            ACTION_MULTIMEDIA_PLAY_PAUSE -> sendReport(RemoteButtonProperties.PlayPauseButton.input)
            ACTION_MULTIMEDIA_PREVIOUS -> sendReport(RemoteButtonProperties.RewindButton.input)
            ACTION_MULTIMEDIA_NEXT -> sendReport(RemoteButtonProperties.ForwardButton.input)
            ACTION_LEFT -> sendReport(RemoteButtonProperties.MenuLeftButton.input)
            ACTION_RIGHT -> sendReport(RemoteButtonProperties.MenuRightButton.input)
            ACTION_UP -> sendReport(RemoteButtonProperties.MenuUpButton.input)
            ACTION_DOWN -> sendReport(RemoteButtonProperties.MenuDownButton.input)
            ACTION_PICK -> sendReport(RemoteButtonProperties.MenuPickButton.input)
            ACTION_BACK -> sendReport(RemoteButtonProperties.BackButton.input)
            ACTION_HOME -> sendReport(RemoteButtonProperties.HomeButton.input)
            ACTION_DISCONNECT -> {
                useCase.disconnectDevice()
                /*useCase.stopHidProfile()
                val serviceIntent = Intent(context, BluetoothHidService::class.java)
                context?.stopService(serviceIntent)*/
            }
        }
    }

    private fun sendReport(bytes: ByteArray) {
        useCase.sendRemoteReport(bytes)
        useCase.sendRemoteReport(REMOTE_INPUT_NONE)
    }
}