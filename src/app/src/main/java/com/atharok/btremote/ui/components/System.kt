// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.components

import com.atharok.btremote.common.extensions.setFullScreen
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun SystemBroadcastReceiver(
    systemAction: String,
    onSystemEvent: (intent: Intent?) -> Unit
) {
    val context = LocalContext.current
    val currentOnSystemEvent by rememberUpdatedState(onSystemEvent)

    DisposableEffect(context, systemAction) {
        val intentFilter = IntentFilter(systemAction)
        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                currentOnSystemEvent(intent)
            }
        }

        androidx.core.content.ContextCompat.registerReceiver(context,broadcast,intentFilter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)

        onDispose {
            context.unregisterReceiver(broadcast)
        }
    }
}

/** Broadcasts are hints: always read the adapter again, including after returning from Settings. */
@Composable
fun CheckBluetoothStateChanged(
    readEnabled: () -> Boolean,
    onBluetoothEnabledChanged: (Boolean) -> Unit
) {
    val scope=rememberCoroutineScope()
    val read by rememberUpdatedState(readEnabled)
    val changed by rememberUpdatedState(onBluetoothEnabledChanged)
    fun refresh() { changed(runCatching { read() }.getOrDefault(false)) }
    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { intent ->
        if(intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) refresh()
    }
    OnLifecycleEvent { _, event -> if(event==Lifecycle.Event.ON_RESUME) scope.launch {
        refresh();delay(300);refresh();delay(700);refresh()
    } }
}

/** Temporary gamepad immersion; restore the app preference instead of changing it. */
@Composable
fun GamepadFullScreen(enabled: Boolean, restoreFullScreen: Boolean) {
    val context = LocalContext.current
    val restore by rememberUpdatedState(restoreFullScreen)
    DisposableEffect(context, enabled) {
        if (enabled) context.setFullScreen(true)
        onDispose { if (enabled) context.setFullScreen(restore) }
    }
    OnLifecycleEvent { _, event ->
        if (enabled && event == Lifecycle.Event.ON_RESUME) context.setFullScreen(true)
    }
}
