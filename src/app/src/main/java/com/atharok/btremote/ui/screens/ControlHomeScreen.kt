// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atharok.btremote.automation.HomeModuleOrder
import com.atharok.btremote.automation.PhoneControlMode
import com.atharok.btremote.ui.components.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Selection is shared by modules; a card click is the only way to enter control. */
@Composable
fun ControlHomeScreen(
    selectedName: String?, selectedAddress: String, enabled: Boolean,
    onModule: (PhoneControlMode) -> Unit, onChooseDevice: () -> Unit,
    onSettings: () -> Unit, onClose: () -> Unit,
    connectionStatus: String = "未连接",
    layoutPreferences: SharedPreferences = LocalContext.current.getSharedPreferences("home_layout_private", Context.MODE_PRIVATE)
) {
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 480
    val columns = if (compact && configuration.screenWidthDp >= 600) 4 else 2
    var help by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var savedOrder by rememberSaveable { mutableStateOf(layoutPreferences.getString("module_order", "") ?: "") }
    val order = remember(savedOrder) { HomeModuleOrder.decode(savedOrder) }
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        if (editing && enabled) {
            savedOrder = HomeModuleOrder.encode(HomeModuleOrder.move(order, from.key.toString(), to.key.toString()))
            layoutPreferences.edit().putString("module_order", savedOrder).apply()
        }
    }
    BackHandler { if (editing) editing = false else onClose() }
    AppScaffold("AiPhoneKVM", topBarActions = {
        HelpIconButton({ help = true })
        BasicIconButton({ if (enabled) editing = !editing },
            if (editing) Icons.Outlined.Done else Icons.Outlined.Edit,
            if (editing) "完成编辑" else "编辑图标")
        SettingsIconButton({ editing = false; onSettings() })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(columns = GridCells.Fixed(columns), state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("home-modules"), contentPadding = PaddingValues(if (compact) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (editing) "拖动图标调整位置 · 完成后退出" else "选择功能",
                        style = MaterialTheme.typography.titleMedium)
                }
                items(order, key = { it.name }) { mode ->
                    ReorderableItem(reorderState, key = mode.name) { dragging ->
                        val (subtitle, icon) = modulePresentation(mode)
                        ModuleCard(mode.title, subtitle, icon, enabled,
                            { if (!editing) onModule(mode) },
                            Modifier.draggableHandle(enabled = editing && enabled),
                            retro = mode == PhoneControlMode.FC, editing = editing, dragging = dragging, compact = compact)
                    }
                }
            }
            HorizontalDivider()
            Surface(onClick = onChooseDevice, enabled = enabled && !editing,
                modifier = Modifier.fillMaxWidth().padding(if (compact) 8.dp else 16.dp).semantics { contentDescription = "蓝牙设备" },
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(if (compact) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.Bluetooth, null, Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text("蓝牙设备", style = MaterialTheme.typography.titleMedium)
                        Text(if (selectedName != null) "当前：$selectedName" else if (selectedAddress.isNotBlank()) "原设备未配对，请重新选择" else "尚未选择 · 点击查看已配对设备",
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(connectionStatus, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
    }
    if (help) SimpleDialog(confirmButtonText = "知道了", dismissButtonText = "关闭",
        onConfirmation = { help = false }, onDismissRequest = { help = false },
        dialogTitle = "选择设备与功能", dialogText = "先点首页最下面的“蓝牙设备”，选择已配对设备，再回首页进入 AI、手柄、键鼠、键盘、媒体、鼠标或 TV。设备页可搜索新设备或允许其他设备发现本机。选择设备不会进入操作页。点击设置左边的编辑图标可拖动调整功能顺序，点击完成或返回退出编辑，位置自动保存。切换功能或退出时会释放按键。")
}

private fun modulePresentation(mode: PhoneControlMode): Pair<String, ImageVector> = when (mode) {
    PhoneControlMode.AI -> "截图或摄像头观察" to Icons.Outlined.SmartToy
    PhoneControlMode.XBOX -> "双摇杆 · 扳机" to Icons.Outlined.SportsEsports
    PhoneControlMode.FC -> "十字键 · B / A" to Icons.Outlined.VideogameAsset
    PhoneControlMode.KEY_MOUSE -> "竖屏键盘 · 触控板" to Icons.Outlined.PhoneAndroid
    PhoneControlMode.KEYBOARD -> "横屏键盘" to Icons.Outlined.Keyboard
    PhoneControlMode.MEDIA -> "亮度 · 音量 · 播放" to Icons.Outlined.PlayCircle
    PhoneControlMode.MOUSE -> "触控板 · 空鼠" to Icons.Outlined.Mouse
    PhoneControlMode.TV -> "电视遥控" to Icons.Outlined.Tv
    PhoneControlMode.MANUAL -> error("Legacy mode has no home card")
}

@Composable
private fun ModuleCard(title: String, subtitle: String, icon: ImageVector,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, retro: Boolean = false,
    editing: Boolean = false, dragging: Boolean = false, compact: Boolean = false) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(if (compact) 88.dp else 136.dp).semantics { contentDescription = title },
        shape = RoundedCornerShape(18.dp), shadowElevation = if (dragging) 8.dp else 0.dp,
        color = if (retro) Color(0xfff1dfd8) else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (retro) Color(0xff81333c) else MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Icon(icon, null, Modifier.size(if (compact) 22.dp else 30.dp))
                Text(title, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (editing) Icon(Icons.Outlined.DragIndicator, null, Modifier.align(Alignment.TopEnd).size(24.dp))
        }
    }
}
