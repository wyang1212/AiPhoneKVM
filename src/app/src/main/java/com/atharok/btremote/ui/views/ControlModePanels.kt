// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.views

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardKey
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardReportState
import com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard.CharacterAdvancedKeyboardKey
import com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard.AdvancedKeyboardKey
import com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard.IconAdvancedKeyboardKey
import com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard.EnglishUSAdvancedKeyboardLayout
import com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard.TextAdvancedKeyboardKey
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.ui.components.customButtons.SurfaceButton
import com.atharok.btremote.ui.views.mouse.MousePadLayout

enum class ControlMode(val label: String, val icon: ImageVector) {
    ENGLISH("键盘", Icons.Outlined.Keyboard),
    MEDIA("媒体", Icons.Outlined.PlayCircle),
    TOUCHPAD("鼠标", Icons.Outlined.Mouse),
    GAMEPAD("Xbox", Icons.Outlined.SportsEsports),
    FC("FC", Icons.Outlined.SportsEsports),
    ORIGINAL("TV", Icons.Outlined.Tv)
}

@Composable
fun EnglishKeyboardPanel(send: (ByteArray) -> Unit, settings: RemoteSettings,
    sendMouse: (MouseAction, Float, Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panelModifier = modifier.semantics { contentDescription = if (landscape) "横屏键盘操作区" else "键鼠操作区" }
    key(landscape) {
        if (landscape) LandscapeEnglishKeyboardPanel(send, panelModifier) else CompactEnglishKeyboardPanel(send, settings, sendMouse, panelModifier)
    }
}

@Composable
private fun LandscapeEnglishKeyboardPanel(send: (ByteArray) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val layout = remember(context) {
        val original = EnglishUSAdvancedKeyboardLayout(context)
        fun ordinary(keys: List<AdvancedKeyboardKey>) = keys.map { it to false }
        fun action(properties: RemoteButtonProperties, weight: Float = 1f) = IconAdvancedKeyboardKey(properties, weight) to false
        fun modifier(key: KeyboardKey, text: String) = TextAdvancedKeyboardKey(key.byte, 1.2f, text) to true
        listOf(
            listOf(TextAdvancedKeyboardKey(KeyboardKey.KEY_ESCAPE.byte, 1.2f, "Esc") to false) + ordinary(original.layout[0].toList()),
            ordinary(listOf(original.line4.first()) + original.line1.toList()) + action(RemoteButtonProperties.KeyboardBackspaceButton, 1.7f),
            listOf(action(RemoteButtonProperties.KeyboardTabButton, 1.3f)) + ordinary(original.line2.toList()) + ordinary(listOf(original.line3.last())),
            listOf(modifier(KeyboardKey.KEY_CTRL_LEFT, "Ctrl")) + ordinary(original.line3.dropLast(1)) + action(RemoteButtonProperties.KeyboardEnterButton, 1.8f),
            listOf(modifier(KeyboardKey.KEY_SHIFT_LEFT, "Shift")) + ordinary(original.line4.drop(2)) + action(RemoteButtonProperties.KeyboardUpButton, 1.3f),
            listOf(modifier(KeyboardKey.KEY_META_LEFT, "Meta"), modifier(KeyboardKey.KEY_ALT, "Alt"),
                action(RemoteButtonProperties.KeyboardSpaceBarButton, 6f), modifier(KeyboardKey.KEY_ALT_GR, "Alt Gr"),
                action(RemoteButtonProperties.KeyboardLeftButton, 1.3f), action(RemoteButtonProperties.KeyboardDownButton, 1.3f), action(RemoteButtonProperties.KeyboardRightButton, 1.3f))
        )
    }
    val reportState = remember { KeyboardReportState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) send(reportState.reset())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            send(reportState.reset())
        }
    }
    BoxWithConstraints(modifier.padding(8.dp)) {
        // Six complete rows fit a phone in landscape; very short windows can still scroll.
        val rowHeight = (maxHeight / layout.size).coerceAtLeast(32.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            layout.forEach { row ->
                Row(Modifier.fillMaxWidth().height(rowHeight - 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { (item, modifierKey) ->
                        val colors = keyboardKeyColors(
                            character = (item as? CharacterAdvancedKeyboardKey)?.text?.singleOrNull(),
                            usage = item.byte.toInt() and 0xff,
                            modifier = modifierKey
                        )
                        SurfaceButton(
                            touchDown = { send(reportState.press(item.byte, modifierKey)) },
                            touchUp = { send(reportState.release(item.byte, modifierKey)) },
                            modifier = Modifier.weight(item.weight).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp), elevation = 1.dp
                        ) {
                            CompositionLocalProvider(LocalContentColor provides colors.foreground) {
                                Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
                                    when (item) {
                                        is CharacterAdvancedKeyboardKey -> {
                                            Text(item.text, fontSize = 18.sp, maxLines = 1)
                                            item.textSecondary?.let { secondary ->
                                                Text(secondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp))
                                            }
                                        }
                                        is TextAdvancedKeyboardKey -> Text(item.text, fontSize = 12.sp, maxLines = 1)
                                        is IconAdvancedKeyboardKey -> Icon(item.properties.icon,
                                            contentDescription = stringResource(item.properties.stringRes), modifier = Modifier.size(22.dp))
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

@Composable
fun MediaControlPanel(send: (ByteArray) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf(RemoteButtonProperties.BrightnessDownButton, RemoteButtonProperties.BrightnessUpButton),
        listOf(RemoteButtonProperties.RewindButton, RemoteButtonProperties.StopButton, RemoteButtonProperties.ForwardButton),
        listOf(RemoteButtonProperties.VolumeDownButton, RemoteButtonProperties.VolumeMuteButton, RemoteButtonProperties.VolumeUpButton),
        listOf(RemoteButtonProperties.PreviousTrackButton, RemoteButtonProperties.PlayPauseButton, RemoteButtonProperties.NextTrackButton)
    )
    Column(modifier.padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    SurfaceButton(
                        touchDown = { send(item.input) }, touchUp = { send(byteArrayOf(0, 0)) },
                        modifier = Modifier.weight(1f).height(100.dp), shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(item.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                            Text(stringResource(item.stringRes), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Text("亮度与媒体键的响应取决于接收手机的系统和当前应用。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TouchpadControlPanel(settings: RemoteSettings,
    send: (MouseAction, Float, Float, Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("单指移动 · 双指滚动 · 点空鼠启停陀螺仪", style = MaterialTheme.typography.bodyMedium)
        MousePadLayout(
            mouseSpeed = settings.mouseSpeed, scrollSpeed = settings.mouseScrollSpeed,
            shouldInvertMouseScrollingDirection = settings.shouldInvertMouseScrollingDirection,
            useGyroscope = false, sendMouseInput = send, coloredButtons = true, integratedControls = true,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
fun ColoredMouseButtons(send: (MouseAction) -> Unit, modifier: Modifier = Modifier,
    airMouseEnabled: Boolean? = null, airMouseAvailable: Boolean = true, onToggleAirMouse: () -> Unit = {}) {
    val buttons = listOf(
        Triple("左键", MouseAction.MOUSE_CLICK_LEFT, Color(0xffd7e9ff)),
        Triple("中键", MouseAction.MOUSE_CLICK_MIDDLE, Color(0xffffdfb4)),
        Triple("右键", MouseAction.MOUSE_CLICK_RIGHT, Color(0xffe9ddff))
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (label, action, color) ->
            SurfaceButton(
                touchDown = { send(action) }, touchUp = { send(MouseAction.NONE) },
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label },
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
                    Text(label, color = Color(0xff172231), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (airMouseEnabled != null) {
            Surface(onClick = onToggleAirMouse, enabled = airMouseAvailable,
                modifier = Modifier.weight(1f).fillMaxHeight().semantics {
                    contentDescription = "空鼠，" + when {
                        !airMouseAvailable -> "此设备无陀螺仪"
                        airMouseEnabled -> "已开启"
                        else -> "已关闭"
                    }
                    selected = airMouseEnabled
                }, shape = RoundedCornerShape(14.dp),
                color = if (airMouseEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("空鼠", style = MaterialTheme.typography.titleMedium)
                    Text(if (!airMouseAvailable) "不可用" else if (airMouseEnabled) "开" else "关",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
