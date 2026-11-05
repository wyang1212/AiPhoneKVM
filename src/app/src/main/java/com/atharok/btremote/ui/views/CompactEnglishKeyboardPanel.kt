// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.ui.views.mouse.MousePadLayout
import com.atharok.btremote.domain.entities.remoteInput.keyboard.*
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.EnglishUSVirtualKeyboardLayout
import com.atharok.btremote.ui.components.customButtons.SurfaceButton

@Composable
fun CompactEnglishKeyboardPanel(send: (ByteArray) -> Unit, settings: RemoteSettings,
    sendMouse: (MouseAction, Float, Float, Float) -> Unit, modifier: Modifier = Modifier) {
    var layer by remember { mutableStateOf(CompactKeyboardLayer.LETTERS) }
    var modifiers by remember { mutableStateOf(CompactModifiers()) }
    val mapping = remember { EnglishUSVirtualKeyboardLayout() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { modifiers = CompactModifiers(); send(byteArrayOf(0, 0)) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); send(byteArrayOf(0, 0)) }
    }
    fun switchLayer(target: CompactKeyboardLayer) {
        send(byteArrayOf(0, 0))
        modifiers = modifiers.copy(shift = ShiftMode.OFF)
        layer = target
    }
    fun press(item: CompactKey) {
        when (item.label) {
            "Shift" -> modifiers = modifiers.toggleShift()
            "123" -> switchLayer(CompactKeyboardLayer.NUMBERS)
            "#+=" -> switchLayer(CompactKeyboardLayer.SYMBOLS)
            "ABC" -> switchLayer(CompactKeyboardLayer.LETTERS)
            "功能" -> switchLayer(CompactKeyboardLayer.FUNCTIONS)
            "Ctrl" -> { modifiers = modifiers.copy(control = !modifiers.control); layer = CompactKeyboardLayer.LETTERS }
            "Alt" -> { modifiers = modifiers.copy(alt = !modifiers.alt); layer = CompactKeyboardLayer.LETTERS }
            "Meta" -> { modifiers = modifiers.copy(meta = !modifiers.meta); layer = CompactKeyboardLayer.LETTERS }
            else -> {
                val base = item.character?.let(mapping::getKeyboardKey) ?: byteArrayOf(0, item.usage.toByte())
                send(modifiers.report(base))
                modifiers = modifiers.afterKey(base[1])
            }
        }
    }
    BoxWithConstraints(modifier.padding(8.dp)) {
    val keyHeight = (maxHeight * 0.4f / 4).coerceIn(44.dp, 68.dp)
    Column(Modifier.fillMaxSize()) {
        Text("上方触控板", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val latch = listOfNotNull(if (modifiers.control) "Ctrl" else null,
            if (modifiers.alt) "Alt" else null, if (modifiers.meta) "Meta" else null)
        if (latch.isNotEmpty()) Text(latch.joinToString(" + ") + " 已选", style = MaterialTheme.typography.labelMedium)
        MousePadLayout(
            mouseSpeed = settings.mouseSpeed, scrollSpeed = settings.mouseScrollSpeed,
            shouldInvertMouseScrollingDirection = settings.shouldInvertMouseScrollingDirection,
            useGyroscope = false, sendMouseInput = sendMouse, coloredButtons = true, integratedControls = true,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
        )
        Box(Modifier.fillMaxWidth()) {
            // Four phone-style rows stay below the integrated touchpad.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                key(layer) {
                    CompactKeyboard.rows(layer).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(keyHeight), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { item ->
                                if (item.spacer) Spacer(Modifier.weight(item.weight)) else {
                                    val shifted = modifiers.shift != ShiftMode.OFF
                                    val label = when {
                                        item.label == "Shift" -> if (modifiers.shift == ShiftMode.LOCKED) "⇪" else "⇧"
                                        item.character?.isLetter() == true && shifted -> item.label.uppercase()
                                        else -> item.label
                                    }
                                    val active = item.label == "Shift" && shifted
                                    val colors = keyboardKeyColors(
                                        character = item.character,
                                        usage = item.usage,
                                        modifier = item.label in listOf("Shift", "Ctrl", "Alt", "Meta"),
                                        layerSwitch = item.label in listOf("ABC", "123", "#+=", "功能"),
                                        active = active
                                    )
                                    SurfaceButton(
                                        touchDown = { press(item) }, touchUp = { send(byteArrayOf(0, 0)) },
                                        modifier = Modifier.weight(item.weight).fillMaxHeight().semantics { contentDescription = label },
                                        shape = RoundedCornerShape(10.dp), elevation = 1.dp
                                    ) {
                                        CompositionLocalProvider(LocalContentColor provides colors.foreground) {
                                            Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
                                                when (item.label) {
                                                    "退格" -> Icon(AppIcons.KeyboardBackspace, contentDescription = null)
                                                    "回车" -> Icon(AppIcons.KeyboardEnter, contentDescription = null)
                                                    else -> Text(label, fontSize = if (item.label.length > 1) 15.sp else 23.sp, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
