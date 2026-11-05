package com.atharok.btremote.ui.components

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

@Composable
fun PhysicalVolumeButtonsHandler(
    onVolumeUpPressed: () -> Boolean,
    onVolumeUpReleased: () -> Boolean,
    onVolumeDownPressed: () -> Boolean,
    onVolumeDownReleased: () -> Boolean,
    view: View = LocalView.current
) {
    DisposableEffect(view) {
        val keyEventDispatcher = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> onVolumeUpPressed()
                        KeyEvent.ACTION_UP -> onVolumeUpReleased()
                        else -> true
                    }
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> onVolumeDownPressed()
                        KeyEvent.ACTION_UP -> onVolumeDownReleased()
                        else -> true
                    }
                }

                else -> false
            }
        }

        ViewCompat.addOnUnhandledKeyEventListener(view, keyEventDispatcher)

        onDispose {
            ViewCompat.removeOnUnhandledKeyEventListener(view, keyEventDispatcher)
        }
    }
}